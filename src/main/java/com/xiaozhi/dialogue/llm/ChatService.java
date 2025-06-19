package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.api.StreamResponseListener;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.ChatMemoryStore;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.utils.EmojiUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * 负责管理和协调LLM相关功能
 * TODO 重构：改成Domain Entity: ChatRole(聊天角色)，管理对话历史记录，管理对话工具调用等。
 */
@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String TOOL_CONTEXT_SESSION_KEY = "session";

    // 句子结束标点符号模式（中英文句号、感叹号、问号）
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile("[。！？!?]");

    // 逗号、分号等停顿标点
    private static final Pattern PAUSE_PATTERN = Pattern.compile("[，、；,;]");

    // 冒号和引号等特殊标点
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[：:\"]");

    // 换行符
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\n\r]");

    // 数字模式（用于检测小数点是否在数字中）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.\\d+");

    // 表情符号模式
    private static final Pattern EMOJI_PATTERN = Pattern.compile("\\p{So}|\\p{Sk}|\\p{Sm}");

    // 最小句子长度（字符数）
    private static final int MIN_SENTENCE_LENGTH = 5;

    // 新句子判断的字符阈值
    private static final int NEW_SENTENCE_TOKEN_THRESHOLD = 8;

    // 历史记录默认限制数量
    private static final int DEFAULT_HISTORY_LIMIT = 10;

    @Resource
    private ChatMemoryStore chatMemoryStore;

    // TODO 移到构建者模式，由连接通过认证，可正常对话时，创建实例，构建好一个完整的Role.
    @Resource
    private ChatModelFactory chatModelFactory;

    /**
     * 处理用户查询（同步方式）
     * 
     * @param session         会话信息
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     * @return 模型回复
     */
    public String chat(ChatSession session, String message, boolean useFunctionCall) {
        try {
            SysDevice device = session.getSysDevice();

            // 获取ChatModel
            ChatModel chatModel = chatModelFactory.takeChatModel(session);

            if (session.getChatMemory() == null) {// 如果记忆没初始化，则初始化一下
                initializeHistory(session);
            }

            // 获取格式化的历史记录（包含当前用户消息）
            List<Message> historyMessages = session.getHistoryMessages();

            ChatOptions chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(useFunctionCall ? session.getToolCallbacks() : new ArrayList<>())
                    .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                    .build();
            UserMessage userMessage = new UserMessage(message);
            Prompt prompt = Prompt.builder().messages(historyMessages).messages(userMessage).chatOptions(chatOptions)
                    .build();

            ChatResponse chatResponse = chatModel.call(prompt);
            if (chatResponse == null || chatResponse.getResult().getOutput().getText() == null) {
                logger.warn("模型响应为空或无生成内容");
                return "抱歉，我在处理您的请求时遇到了问题。请稍后再试。";
            }
            String response = chatResponse.getResult().getOutput().getText();
            boolean hasToolCalls = chatResponse.hasToolCalls();
            String messageType = SysMessage.MESSAGE_TYPE_NORMAL;// 默认消息类型为普通消息
            if (!hasToolCalls) {// 非function消息才加入对话历史，避免调用混乱
                // 更新历史消息缓存
                session.addHistoryMessage(userMessage);
                if (response != null && !response.isEmpty()) {
                    session.addHistoryMessage(new AssistantMessage(response));
                }
            } else {
                // TODO 后续还需要根据元数据判断是function_call还是mcp调用
                // 检查元数据中是否包含工具调用标识
                // 发生了工具调用，获取函数调用的名称，通过名称反查类型
                // String functionName = chatResponse.getMetadata().get("function_name");
                messageType = SysMessage.MESSAGE_TYPE_FUNCTION_CALL;// function消息类型
            }
            final String finalMessageType = messageType;
            Thread.startVirtualThread(() -> {// 异步持久化
                // 保存用户消息，会被持久化至数据库。
                this.addUserMessage(device, message, finalMessageType);
                if (response != null && !response.isEmpty()) {
                    // 保存AI消息，会被持久化至数据库。
                    this.addAssistantMessage(device, response, finalMessageType);
                }
            });
            return response;

        } catch (Exception e) {
            logger.error("处理查询时出错: {}", e.getMessage(), e);
            return "抱歉，我在处理您的请求时遇到了问题。请稍后再试。";
        }
    }

    /**
     * 处理用户查询（流式方式）
     *
     * @param device          设备信息
     * @param message         用户消息
     * @param useFunctionCall 是否使用函数调用
     */
    public Flux<ChatResponse> chatStream(ChatSession session, SysDevice device, String message,
            boolean useFunctionCall) {
        // 获取ChatModel
        ChatModel chatModel = chatModelFactory.takeChatModel(session);

        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(useFunctionCall ? session.getToolCallbacks() : new ArrayList<>())
                .toolContext(TOOL_CONTEXT_SESSION_KEY, session)
                .build();

        if (session.getChatMemory() == null) {// 如果记忆没初始化，则初始化一下
            initializeHistory(session);
        }
        // 获取格式化的历史记录（包含当前用户消息）
        List<Message> historyMessages = session.getHistoryMessages();

        UserMessage userMessage = new UserMessage(message);
        historyMessages.add(userMessage);
        Prompt prompt = Prompt.builder().messages(historyMessages).chatOptions(chatOptions).build();

        // 调用实际的流式聊天方法
        // return chatModel.stream(prompt).map(response -> (response.getResult() == null
        // || response.getResult().getOutput() == null
        // || response.getResult().getOutput().getText() == null) ? ""
        // : response.getResult().getOutput().getText());
        return chatModel.stream(prompt);
    }

    public void chatStreamBySentence(ChatSession session, String message, boolean useFunctionCall,
            TriConsumer<String, Boolean, Boolean> sentenceHandler) {
        try {
            SysDevice device = session.getSysDevice();
            device.setSessionId(session.getSessionId());
            // 创建流式响应监听器
            StreamResponseListener streamListener = new TokenStreamResponseListener(session, message, sentenceHandler);
            final StringBuilder toolName = new StringBuilder(); // 当前句子的缓冲区
            // 调用现有的流式方法
            chatStream(session, device, message, useFunctionCall)
                    .subscribe(
                            chatResponse -> {
                                String token = chatResponse.getResult() == null
                                        || chatResponse.getResult().getOutput() == null
                                        || chatResponse.getResult().getOutput().getText() == null ? ""
                                                : chatResponse.getResult().getOutput().getText();
                                if (!token.isEmpty()) {
                                    streamListener.onToken(token);
                                }
                                if (toolName.isEmpty() && useFunctionCall) {
                                    Generation generation = chatResponse.getResult();
                                    // 注意，不能用chatResponse.hasToolCalls()判断，当前chatResponse工具调用结果的返回，
                                    // 是个文本类助手消息，hasToolCalls标识是false。必须溯源取meta
                                    if (generation != null) {
                                        ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
                                        String name = chatGenerationMetadata.get("toolName");
                                        if (name != null && !name.isEmpty()) {
                                            toolName.append(name);
                                        }
                                    }
                                }
                            },
                            streamListener::onError,
                            () -> streamListener.onComplete(toolName.toString()));
        } catch (Exception e) {
            logger.error("处理LLM时出错: {}", e.getMessage(), e);
            // 发送错误信号
            sentenceHandler.accept("抱歉，我在处理您的请求时遇到了问题。", true, true);
        }
    }

    /**
     * 初始化设备的历史记录缓存
     *
     */
    public void initializeHistory(ChatSession chatSession) {
        if (chatSession.getSysDevice() == null) {
            return;
        }
        SysDevice device = chatSession.getSysDevice();
        // 如果缓存中不存在该设备的历史记录，则初始化缓存。默认情况下，只缓存当前会话的聊天记录。
        // 同一个设备重新连接至服务器，会被标识为不同的sessionId。
        // 可以将这理解为spring-ai的conversation会话,将sessionId作为conversationId
        // 从数据库加载历史记录
        List<SysMessage> history = chatMemoryStore.getMessages(device.getDeviceId(),
                SysMessage.MESSAGE_TYPE_NORMAL, DEFAULT_HISTORY_LIMIT);
        String systemMessage = chatMemoryStore.getSystemMessage(device.getDeviceId(), device.getRoleId());
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(DEFAULT_HISTORY_LIMIT)
                .build();// 创建一个新的MessageWindowChatMemory实例，限制为10条消息滚动
        chatMemory.add(device.getSessionId(), new SystemMessage(systemMessage));
        chatMemory.add(device.getSessionId(), convert(history));
        chatSession.setChatMemory(chatMemory);
        logger.info("已初始化设备 {} 的历史记录缓存，共 {} 条消息", device.getDeviceId(), history.size());
    }

    /**
     * 清除设备缓存
     * 
     * @param deviceId 设备ID
     */
    public void clearMessageCache(String deviceId) {
        chatMemoryStore.clearMessages(deviceId);
    }

    /**
     * 添加用户消息
     *
     * @param message 用户消息
     */
    public void addUserMessage(SysDevice device, String message, String messageType) {
        // 更新缓存
        chatMemoryStore.addMessage(device.getDeviceId(), device.getSessionId(), "user", message,
                device.getRoleId(), messageType, null);
    }

    /**
     * 添加AI消息
     *
     * @param message AI消息
     */
    public void addAssistantMessage(SysDevice device, String message, String messageType) {
        chatMemoryStore.addMessage(device.getDeviceId(), device.getSessionId(), "assistant", message,
                device.getRoleId(), messageType, null);
    }

    /**
     * 通用添加消息
     *
     * @param message     消息内容
     * @param role        角色名称
     * @param messageType 消息类型
     */
    public void addMessage(SysDevice device, String message, String role, String messageType, String audioPath) {
        chatMemoryStore.addMessage(device.getDeviceId(), device.getSessionId(), role, message, device.getRoleId(),
                messageType, audioPath);
    }

    /**
     * 将数据库记录的SysMessag转换为spring-ai的Message。
     * 加载的历史都是普通消息(SysMessage.MESSAGE_TYPE_NORMAL)
     * 
     * @param messages
     * @return
     */
    private List<Message> convert(List<SysMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages.stream()
                .filter(message -> MessageType.ASSISTANT.getValue().equals(message.getSender())
                        || MessageType.USER.getValue().equals(message.getSender()))
                .map(message -> {
                    String role = message.getSender();
                    // 一般消息("messageType", "NORMAL");//默认为普通消息
                    Map<String, Object> metadata = Map.of("messageId", message.getMessageId(), "messageType",
                            message.getMessageType());
                    return switch (role) {
                        case "assistant" -> new AssistantMessage(message.getMessage(), metadata);
                        case "user" -> UserMessage.builder().text(message.getMessage()).metadata(metadata).build();
                        default -> throw new IllegalArgumentException("Invalid role: " + role);
                    };
                }).collect(Collectors.toList());
    }

    /**
     * 判断文本是否包含实质性内容（不仅仅是空白字符或标点符号）
     *
     * @param text 要检查的文本
     * @return 是否包含实质性内容
     */
    private boolean containsSubstantialContent(String text) {
        if (text == null || text.trim().length() < MIN_SENTENCE_LENGTH) {
            return false;
        }

        // 移除所有标点符号和空白字符后，检查是否还有内容
        String stripped = text.replaceAll("[\\p{P}\\s]", "");
        return stripped.length() >= 2; // 至少有两个非标点非空白字符
    }

    /**
     * 三参数消费者接口
     */
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    class TokenStreamResponseListener implements StreamResponseListener {

        final StringBuilder currentSentence = new StringBuilder(); // 当前句子的缓冲区
        final StringBuilder contextBuffer = new StringBuilder(); // 上下文缓冲区，用于检测数字中的小数点
        final AtomicInteger sentenceCount = new AtomicInteger(0); // 已发送句子的计数
        final StringBuilder fullResponse = new StringBuilder(); // 完整响应的缓冲区
        final AtomicBoolean finalSentenceSent = new AtomicBoolean(false); // 跟踪最后一个句子是否已发送
        String message;// 用户消息内容
        ChatSession session;
        TriConsumer<String, Boolean, Boolean> sentenceHandler;

        public TokenStreamResponseListener(ChatSession session, String message,
                TriConsumer<String, Boolean, Boolean> sentenceHandler) {
            this.message = message;
            this.session = session;
            this.sentenceHandler = sentenceHandler;
        }

        @Override
        public void onToken(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }
            // 将token添加到完整响应
            fullResponse.append(token);

            // 逐字符处理token
            for (int i = 0; i < token.length();) {
                int codePoint = token.codePointAt(i);
                String charStr = new String(Character.toChars(codePoint));

                // 将字符添加到上下文缓冲区（保留最近的字符以检测数字模式）
                contextBuffer.append(charStr);
                if (contextBuffer.length() > 20) { // 保留足够的上下文
                    contextBuffer.delete(0, contextBuffer.length() - 20);
                }

                // 将字符添加到当前句子缓冲区
                currentSentence.append(charStr);

                // 检查各种断句标记
                boolean shouldSendSentence = false;
                boolean isEndMark = SENTENCE_END_PATTERN.matcher(charStr).find();
                boolean isPauseMark = PAUSE_PATTERN.matcher(charStr).find();
                boolean isSpecialMark = SPECIAL_PATTERN.matcher(charStr).find();
                boolean isNewline = NEWLINE_PATTERN.matcher(charStr).find();
                boolean isEmoji = EmojiUtils.isEmoji(codePoint);

                // 检查当前句子是否包含颜文字
                boolean containsKaomoji = false;
                if (currentSentence.length() >= 3) { // 颜文字至少需要3个字符
                    containsKaomoji = EmojiUtils.containsKaomoji(currentSentence.toString());
                }

                // 如果当前字符是句号，检查它是否是数字中的小数点
                if (isEndMark && charStr.equals(".")) {
                    String context = contextBuffer.toString();
                    Matcher numberMatcher = NUMBER_PATTERN.matcher(context);
                    // 如果找到数字模式（如"0.271"），则不视为句子结束标点
                    if (numberMatcher.find() && numberMatcher.end() >= context.length() - 3) {
                        isEndMark = false;
                    }
                }

                // 判断是否应该发送当前句子
                if (isEndMark) {
                    // 句子结束标点是强断句信号
                    shouldSendSentence = true;
                } else if (isNewline) {
                    // 换行符也是强断句信号
                    shouldSendSentence = true;
                } else if ((isPauseMark || isSpecialMark || isEmoji || containsKaomoji)
                        && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    // 停顿标点、特殊标点、表情符号或颜文字在句子足够长时可以断句
                    shouldSendSentence = true;
                }

                // 如果应该发送句子，且当前句子长度满足要求
                if (shouldSendSentence && currentSentence.length() >= MIN_SENTENCE_LENGTH) {
                    String sentence = currentSentence.toString().trim();

                    // 过滤颜文字
                    sentence = EmojiUtils.filterKaomoji(sentence);

                    if (containsSubstantialContent(sentence)) {
                        boolean isFirst = sentenceCount.get() == 0;
                        boolean isLast = false; // 只有在onComplete中才会有最后一个句子

                        sentenceHandler.accept(sentence, isFirst, isLast);
                        sentenceCount.incrementAndGet();

                        // 清空当前句子缓冲区
                        currentSentence.setLength(0);
                    }
                }

                // 移动到下一个码点
                i += Character.charCount(codePoint);
            }
        }

        @Override
        public void onComplete(String toolName) {
            // 检查该会话是否已完成处理
            // 处理当前缓冲区剩余的内容（如果有）
            if (currentSentence.length() > 0 && containsSubstantialContent(currentSentence.toString())
                    && !finalSentenceSent.get()) {
                String sentence = currentSentence.toString().trim();
                boolean isFirst = sentenceCount.get() == 0;
                boolean isLast = true; // 这是最后一个句子

                sentenceHandler.accept(sentence, isFirst, isLast);
                sentenceCount.incrementAndGet();
                finalSentenceSent.set(true);
            } else if (!finalSentenceSent.get()) {
                // 如果没有剩余内容但也没有发送过最后一个句子，发送一个空的最后句子标记
                // 这确保即使没有剩余内容，也会发送最后一个句子标记
                boolean isFirst = sentenceCount.get() == 0;
                sentenceHandler.accept("", isFirst, true);
                finalSentenceSent.set(true);
            }

            persistMessages(toolName);

            // 记录处理的句子数量
            logger.debug("总共处理了 {} 个句子", sentenceCount.get());
        }

        /**
         * 保存消息,只保存用户输入与输出。
         * Message在没有持久化前，是不会有messageId的。
         */
        void persistMessages(String toolName) {
            // TODO 是否需要把content为空和角色为tool的入库? 目前不入库（这类主要是function_call的二次调用llm进行总结时的过程消息）
            // 如果本轮对话是function_call或mcp调用(最后一条信息的类型)，把用户的消息类型也修正为同样类型
            // String lastMessageType = allMessages.get(allMessages.size() -
            // 1).get("messageType").toString();
            // TODO
            // 需要进一步看看ChatModel在流式响应里是如何判断hasTools的，或者直接基于Flux<ChatResponse>已封装好的对象hasToolCalls判断
            boolean hasToolCalls = toolName != null && !toolName.isEmpty();
            String messageType = hasToolCalls ? SysMessage.MESSAGE_TYPE_FUNCTION_CALL : SysMessage.MESSAGE_TYPE_NORMAL;// TODO
                                                                                                                       // 后续可以根据名称区分function还是mcp，来细分类型

            UserMessage userMessage = new UserMessage(message);

            // 获取当前对话ID
            String dialogueId = session.getDialogueId();

            if (!hasToolCalls) {// 非function消息才加入对话历史，避免调用混乱
                session.addHistoryMessage(userMessage);
                if (!fullResponse.isEmpty()) {
                    AssistantMessage assistantMessage = new AssistantMessage(fullResponse.toString());
                    session.addHistoryMessage(assistantMessage);
                }
            }
            Thread.startVirtualThread(() -> {// 异步持久化
                String userAudioPath = session.getUserAudioPath();
                addMessage(session.getSysDevice(), userMessage.getText(), userMessage.getMessageType().getValue(),
                        messageType, userAudioPath);
                if (!fullResponse.isEmpty()) {
                    AssistantMessage assistantMessage = new AssistantMessage(fullResponse.toString());
                    String assistAudioPath = session.getAssistantAudioPath();
                    addMessage(session.getSysDevice(), assistantMessage.getText(),
                            assistantMessage.getMessageType().getValue(), messageType, assistAudioPath);
                }
            });
        }

        @Override
        public void onError(Throwable e) {
            logger.error("流式响应出错: {}", e.getMessage(), e);
            // 发送错误信号
            sentenceHandler.accept("抱歉，我在处理您的请求时遇到了问题。", true, true);

        }
    };
}