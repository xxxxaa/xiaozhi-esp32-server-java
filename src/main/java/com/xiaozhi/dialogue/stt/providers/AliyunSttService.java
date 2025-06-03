package com.xiaozhi.dialogue.stt.providers;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public class AliyunSttService implements SttService {
    private static final Logger logger = LoggerFactory.getLogger(AliyunSttService.class);
    private static final String PROVIDER_NAME = "aliyun";
    private static final int QUEUE_TIMEOUT_MS = 100; // 队列等待超时时间
    private static final int QUEUE_CAPACITY = 1000; // 队列容量限制
    private static final String DEFAULT_MODEL = "paraformer-realtime-v2"; // 默认模型
    
    // 识别器实例
    private Recognition recognizer;
    private String apiKey;
    private final Object recognizerLock = new Object(); // 用于同步访问recognizer

    public AliyunSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
        // 初始化recognizer
        createNewRecognizer();
    }

    /**
     * 创建新的Recognition实例
     */
    private void createNewRecognizer() {
        synchronized (recognizerLock) {
            // 如果存在旧的实例，尝试关闭它
            if (recognizer != null) {
                try {
                    if (recognizer.getDuplexApi() != null) {
                        recognizer.getDuplexApi().close(1000, "创建新实例");
                    }
                } catch (Exception e) {
                    logger.warn("关闭旧的Recognition实例时出错", e);
                }
            }
            
            // 创建新实例
            recognizer = new Recognition();
            logger.info("创建了新的Recognition实例");
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String recognition(byte[] audioData) {
        // 单次识别暂未实现，可以根据需要添加
        logger.warn("阿里云单次识别未实现，请使用流式识别");
        return null;
    }

    @Override
    public String streamRecognition(Sinks.Many<byte[]> audioSink) {
        // 使用有界阻塞队列存储音频数据，防止OOM
        BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        AtomicReference<String> finalResult = new AtomicReference<>("");
        AtomicBoolean isProcessing = new AtomicBoolean(true);
        AtomicBoolean recognizerFailed = new AtomicBoolean(false);

        // 获取当前的recognizer实例
        Recognition currentRecognizer;
        synchronized (recognizerLock) {
            currentRecognizer = this.recognizer;
        }

        // 订阅Sink并将数据放入队列
        var subscription = audioSink.asFlux().subscribe(
                data -> {
                    // 如果队列已满，丢弃最旧的数据
                    if (!audioQueue.offer(data)) {
                        audioQueue.poll();
                        audioQueue.offer(data);
                        logger.warn("音频队列已满，丢弃旧数据");
                    }
                },
                error -> {
                    logger.error("音频流处理错误", error);
                    isCompleted.set(true);
                },
                () -> isCompleted.set(true));

        // 创建RxJava Flowable用于阿里云SDK，使用DROP策略处理背压
        Flowable<ByteBuffer> rxAudioStream = Flowable.create(emitter -> {
            try {
                while (isProcessing.get() && (!isCompleted.get() || !audioQueue.isEmpty())) {
                    byte[] audioChunk = null;
                    try {
                        audioChunk = audioQueue.poll(QUEUE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        logger.warn("音频数据队列等待被中断", e);
                        Thread.currentThread().interrupt();
                        break;
                    }

                    if (audioChunk != null) {
                        ByteBuffer buffer = ByteBuffer.wrap(audioChunk);
                        if (!emitter.isCancelled()) {
                            emitter.onNext(buffer);
                        } else {
                            break;
                        }
                    }
                }
                if (!emitter.isCancelled()) {
                    emitter.onComplete();
                }
            } catch (Exception e) {
                logger.error("处理音频流时发生错误", e);
                if (!emitter.isCancelled()) {
                    emitter.onError(e);
                }
            }
        }, BackpressureStrategy.DROP); // 使用DROP策略处理背压

        // 创建识别参数
        RecognitionParam param = RecognitionParam.builder()
                .model(DEFAULT_MODEL)
                .format("pcm")
                .sampleRate(AudioUtils.SAMPLE_RATE) // 使用16000Hz采样率
                .apiKey(apiKey)
                .build();

        try {
            // 使用虚拟线程执行流式识别，设置超时机制
            var recognitionThread = Thread.startVirtualThread(() -> {
                try {
                    // 保存中间结果，用于跟踪识别进度
                    StringBuilder fullText = new StringBuilder();
                    
                    currentRecognizer.streamCall(param, rxAudioStream)
                            .blockingForEach(result -> {
                                if (result != null && result.getSentence() != null) {
                                    String text = result.getSentence().getText();
                                    if (text != null && !text.isEmpty()) {
                                        // 更新结果
                                        fullText.setLength(0);
                                        fullText.append(text);
                                        finalResult.set(text);
                                        
                                        // 记录中间结果，便于调试
                                        if (logger.isDebugEnabled()) {
                                            logger.debug("语音识别中间结果: {}", text);
                                        }
                                    }
                                }
                            });
                            
                    logger.info("语音识别完成，最终结果: {}", finalResult.get());
                } catch (Exception e) {
                    logger.error("阿里云流式语音识别失败", e);
                    recognizerFailed.set(true); // 标记识别器失败
                } finally {
                    isProcessing.set(false);
                    subscription.dispose(); // 清理资源
                    
                    // 如果识别过程中出现异常，需要重置recognizer
                    if (recognizerFailed.get()) {
                        try {
                            // 关闭WebSocket连接
                            if (currentRecognizer.getDuplexApi() != null) {
                                currentRecognizer.getDuplexApi().close(1000, "bye");
                                logger.info("已关闭失败的WebSocket连接");
                            }
                            
                            // 创建新的实例替代当前实例
                            createNewRecognizer();
                        } catch (Exception ex) {
                            logger.error("处理失败的Recognition实例时出错", ex);
                        }
                    }
                }
            });

            // 设置最大等待时间，防止识别过程无限阻塞
            recognitionThread.join(5000); // 最多等待5秒
            
            // 如果超时，强制结束处理
            if (isProcessing.get()) {
                logger.warn("语音识别超时，强制结束");
                isProcessing.set(false);
                recognizerFailed.set(true); // 超时也视为失败，需要重置
            }
            
        } catch (Exception e) {
            logger.error("启动虚拟线程失败", e);
            subscription.dispose(); // 确保资源被释放
            
            // 线程启动失败也视为识别器失败
            recognizerFailed.set(true);
            
            // 处理失败的识别器
            try {
                // 关闭WebSocket连接
                if (currentRecognizer.getDuplexApi() != null) {
                    currentRecognizer.getDuplexApi().close(1000, "bye");
                }
                
                // 创建新的实例替代
                createNewRecognizer();
            } catch (Exception ex) {
                logger.error("处理失败的Recognition实例时出错", ex);
            }
        }

        return finalResult.get();
    }
}