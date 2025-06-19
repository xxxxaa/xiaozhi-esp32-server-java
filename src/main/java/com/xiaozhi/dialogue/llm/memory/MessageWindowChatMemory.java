package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysRoleService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service(value = "messageWindowChatMemory")
public class MessageWindowChatMemory implements ChatMemoryStore{
    private final SysRoleService roleService;
    private final DatabaseChatMemory chatMemory;
    private final int maxMessages;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MessageWindowChatMemory.class);
    // 缓存系统消息，避免频繁查询数据库
    private Map<String, String> systemMessageCache = new ConcurrentHashMap<>();


    @Autowired
    public MessageWindowChatMemory(SysRoleService roleService,DatabaseChatMemory chatMemory,int maxMessages){
        this.roleService = roleService;
        this.chatMemory = chatMemory;
        this.maxMessages = maxMessages;
    }

    @Override
    public void addMessage(String deviceId, String sessionId, String sender, String content, Integer roleId, String messageType, String audioPath) {
        // TODO 修改接口为 使用UserMessage 、 AssistantMessage的概念
        chatMemory.addMessage(deviceId, sessionId, sender, content, roleId, messageType, audioPath);
    }

    @Override
    public List<SysMessage> getMessages(String deviceId, String messageType, Integer limit) {
        return chatMemory.getMessages(deviceId, messageType, limit);
    }

    @Override
    public void clearMessages(String deviceId) {
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
    public void addUserMessage(SysDevice device, String message, String messageType) {
        // 更新缓存
        this.addMessage(device.getDeviceId(), device.getSessionId(), "user", message,
                device.getRoleId(), messageType, null);
    }

    /**
     * 添加AI消息
     *
     * @param message AI消息
     */
    public void addAssistantMessage(SysDevice device, String message, String messageType) {
        this.addMessage(device.getDeviceId(), device.getSessionId(), "assistant", message,
                device.getRoleId(), messageType, null);
    }

}
