package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.Base;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.service.SysRoleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于数据库的聊天记忆实现
 */
@Service
public class DatabaseChatMemory  {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemory.class);

    @Autowired
    private SysMessageService messageService;

    @Autowired
    private TtsServiceFactory ttsService;

    public void addMessage(String deviceId, String sessionId, String sender, String content, Integer roleId, String messageType, String audioPath) {
        // TODO 异步虚拟线程处理持久化。
        Thread.startVirtualThread(() -> {
            try {
                SysMessage message = new SysMessage();
                message.setDeviceId(deviceId);
                message.setSessionId(sessionId);
                message.setSender(sender);
                message.setMessage(content);
                message.setRoleId(roleId);
                message.setMessageType(messageType);
                if (sender == "assistant") {
                    // 目前生成的语音保存采用默认的语音合成服务，后续可以考虑支持自定义语音合成服务
                    // todo
                    message.setAudioPath(ttsService.getDefaultTtsService().textToSpeech(content));
                } else {
                    message.setAudioPath(audioPath);
                }
                messageService.add(message);
            } catch (Exception e) {
                logger.error("保存消息时出错: {}", e.getMessage(), e);
            }
        });
    }


    public List<SysMessage> getMessages(String deviceId, String messageType, Integer limit) {
        try {
            SysMessage queryMessage = new SysMessage();
            queryMessage.setDeviceId(deviceId);
            queryMessage.setMessageType(messageType);
            PageFilter pageFilter = new PageFilter(1, limit);
            List<SysMessage> messages = messageService.query(queryMessage, pageFilter);
            messages = new ArrayList<>(messages);
            messages.sort(Comparator.comparing(Base::getCreateTime));
            return messages;
            // return messages;
        } catch (Exception e) {
            logger.error("获取历史消息时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }


    public void clearMessages(String deviceId) {
        try {
            // 清除设备的历史消息
            SysMessage deleteMessage = new SysMessage();
            deleteMessage.setDeviceId(deviceId);
            // messageService.update(deleteMessage);
        } catch (Exception e) {
            logger.error("清除设备历史记录时出错: {}", e.getMessage(), e);
        }
    }

}