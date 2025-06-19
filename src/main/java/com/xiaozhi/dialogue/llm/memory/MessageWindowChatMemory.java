package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysRoleService;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service(value = "messageWindowChatMemory")
public class MessageWindowChatMemory implements ChatMemoryStore{
    private final SysRoleService roleService;
    private final DatabaseChatMemory chatMemory;
    private final int maxMessages;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageWindowChatMemory.class);

    // 缓存系统消息，避免频繁查询数据库
    private Map<String, String> systemMessageCache = new ConcurrentHashMap<>();

    // 缓存历史消息，只缓存 MESSAGE_TYPE_NORMAL
    private Map<String,List<Message>> historyCache = new ConcurrentHashMap<>();

    @Autowired
    public MessageWindowChatMemory(SysRoleService roleService,DatabaseChatMemory chatMemory,int maxMessages){
        this.roleService = roleService;
        this.chatMemory = chatMemory;
        this.maxMessages = maxMessages;
    }

    @Override
    public List<Message> initHistory(String deviceId) {
        logger.info("获取设备{}的历史消息", deviceId);
        List<SysMessage> history = chatMemory.getMessages(deviceId, SysMessage.MESSAGE_TYPE_NORMAL, maxMessages);
        List<Message> messages =convert( history);
        historyCache.put(deviceId, messages);
        return messages;
    }

    @Override
    public void clearMessages(String deviceId) {
        historyCache.remove(deviceId);
        chatMemory.clearMessages(deviceId);
        // 清除缓存
        systemMessageCache.keySet().removeIf(key -> key.startsWith(deviceId + ":"));
    }

    // todo 在get时处理缩容。
    private List<Message> process(List<Message> memoryMessages, List<Message> newMessages) {
        List<Message> processedMessages = new ArrayList<>();

        Set<Message> memoryMessagesSet = new HashSet<>(memoryMessages);
        boolean hasNewSystemMessage = newMessages.stream()
                .filter(SystemMessage.class::isInstance)
                .anyMatch(message -> !memoryMessagesSet.contains(message));

        memoryMessages.stream()
                .filter(message -> !(hasNewSystemMessage && message instanceof SystemMessage))
                .forEach(processedMessages::add);

        processedMessages.addAll(newMessages);

        if (processedMessages.size() <= this.maxMessages) {
            return processedMessages;
        }

        int messagesToRemove = processedMessages.size() - this.maxMessages;

        List<Message> trimmedMessages = new ArrayList<>();
        int removed = 0;
        for (Message message : processedMessages) {
            if (message instanceof SystemMessage || removed >= messagesToRemove) {
                trimmedMessages.add(message);
            }
            else {
                removed++;
            }
        }

        return trimmedMessages;
    }

    @Override
    public String getSystemMessage(String deviceId, Integer roleId) {

        if (roleId == null) {
            return "";
        }
        String cacheKey = deviceId + ":" + roleId;

        // 先从缓存获取
        if (systemMessageCache.containsKey(cacheKey)) {
            return systemMessageCache.get(cacheKey);
        }

        try {
            // 从数据库获取角色描述
            SysRole role = roleService.selectRoleById(roleId);
            if (role != null && role.getRoleDesc() != null) {
                String systemMessage = role.getRoleDesc();
                // 存入缓存
                systemMessageCache.put(cacheKey, systemMessage);
                return systemMessage;
            }
        } catch (Exception e) {
            logger.error("获取系统消息时出错: {}", e.getMessage(), e);
        }

        return "";
    }

    @Override
    public void setSystemMessage(String deviceId, Integer roleId, String systemMessage) {
        String cacheKey = deviceId + ":" + roleId;

        // 更新缓存
        systemMessageCache.put(cacheKey, systemMessage);

    }

    /**
     * 添加用户消息
     *
     * @param message 用户消息
     */
    public void addMessage(SysDevice device, UserMessage message, String audioPath) {
        // 更新缓存
        historyCache.get(device.getDeviceId()).add( message);
        // 用户消息一定是 MESSAGE_TYPE_NORMAL
        chatMemory.addMessage(device.getDeviceId(), device.getSessionId(), "user", message.getText(),
                device.getRoleId(), SysMessage.MESSAGE_TYPE_NORMAL, audioPath);

    }

    /**
     * 添加AI消息
     *
     * @param message AI消息
     */
    public void addMessage(SysDevice device, AssistantMessage message, String audioPath) {

        boolean hasToolCalls = message.hasToolCalls();
        // 判断消息类型（不是spring-ai的消息类型）
        String messageType = hasToolCalls ? SysMessage.MESSAGE_TYPE_FUNCTION_CALL : SysMessage.MESSAGE_TYPE_NORMAL;

        // 非function消息才加入对话历史，避免调用混乱
        if(!hasToolCalls){
            // 更新缓存
            historyCache.get(device.getDeviceId()).add( message);
        }

        // TODO 后续还需要根据元数据判断是function_call还是mcp调用
        // 检查元数据中是否包含工具调用标识
        // 发生了工具调用，获取函数调用的名称，通过名称反查类型
        // String functionName = chatResponse.getMetadata().get("function_name");
        String response = message.getText();
        if (StringUtils.hasText( response)) {
            chatMemory.addMessage(device.getDeviceId(), device.getSessionId(), "assistant", response,
                    device.getRoleId(), messageType, audioPath);

        }
    }

    public List<Message> prompt(SysDevice device, UserMessage userMessage){
        SystemMessage systemMessage = new SystemMessage(this.getSystemMessage(device.getDeviceId(), device.getRoleId()));
        List<Message> historyMessages = historyCache.get(device.getDeviceId());
        int size = historyMessages.size();
        while( historyMessages.size()>maxMessages){
            historyMessages.remove(0);
        }

        List<Message> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.addAll(historyMessages);
        messages.add(userMessage);

        // 保存用户消息，会被持久化至数据库。
        this.addMessage(device, userMessage,null);

        return messages;
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

}
