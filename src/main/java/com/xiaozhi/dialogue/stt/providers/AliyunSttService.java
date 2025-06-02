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
    
    // 复用识别器实例，避免频繁创建
    private final Recognition recognizer;
    private String apiKey;

    public AliyunSttService(SysConfig config) {
        this.apiKey = config.getApiKey();
        this.recognizer = new Recognition();
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
                    
                    recognizer.streamCall(param, rxAudioStream)
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
                                            logger.info("语音识别中间结果: {}", text);
                                        }
                                    }
                                }
                            });
                            
                    logger.info("语音识别完成，最终结果: {}", finalResult.get());
                } catch (Exception e) {
                    logger.error("阿里云流式语音识别失败", e);
                } finally {
                    isProcessing.set(false);
                    subscription.dispose(); // 清理资源
                }
            });

            // 设置最大等待时间，防止识别过程无限阻塞
            recognitionThread.join(5000); // 最多等待5秒
            
            // 如果超时，强制结束处理
            if (isProcessing.get()) {
                logger.warn("语音识别超时，强制结束");
                isProcessing.set(false);
            }
            
        } catch (Exception e) {
            logger.error("启动虚拟线程失败", e);
            subscription.dispose(); // 确保资源被释放
        }

        return finalResult.get();
    }
}