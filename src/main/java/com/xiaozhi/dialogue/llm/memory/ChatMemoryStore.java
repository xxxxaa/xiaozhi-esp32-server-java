package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/**
 * 聊天记忆接口
 * TODO 需要进一步抽象出一个Conversation的接口
 * 负责管理聊天历史记录
 */
public interface ChatMemoryStore {
    // 历史记录默认限制数量
    int DEFAULT_HISTORY_LIMIT = 10;

    void addMessage(SysDevice device, UserMessage message, String audioPath);

    void addMessage(SysDevice device, AssistantMessage message, String audioPath);

    List<Message> prompt(SysDevice device,UserMessage userMessage);

    /**
     * 初始化历史消息
     *
     * @param deviceId 设备标识
     * @return 历史消息列表
     */
    List<Message> initHistory(String deviceId);

    /**
     * 清除设备的历史记录
     * 
     * @param deviceId 设备ID
     */
    void clearMessages(String deviceId);
    
    /**
     * 获取系统消息
     * 
     * @param deviceId 设备ID
     * @param roleId 角色ID
     * @return 系统消息
     */
    String getSystemMessage(String deviceId, Integer roleId);
    
    /**
     * 设置系统消息
     * 
     * @param deviceId 设备ID
     * @param roleId 角色ID
     * @param systemMessage 系统消息
     */
    void setSystemMessage(String deviceId, Integer roleId, String systemMessage);
}