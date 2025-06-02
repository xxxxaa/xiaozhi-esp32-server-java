package com.xiaozhi.communication.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.service.AudioService;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.dialogue.service.IotService;
import com.xiaozhi.dialogue.service.VadService;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private AudioService audioService;

    @Resource
    private TtsServiceFactory ttsService;

    @Resource
    private VadService vadService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private ConfigManager configManager;

    @Resource
    private DialogueService dialogueService;

    @Resource
    private IotService iotService;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private SttServiceFactory sttFactory;

    @Resource
    private ChatService chatService;

    @Resource
    private ChatModelFactory  chatModelFactory;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private SysRoleService sysRoleService;

    // 用于存储设备ID和验证码生成状态的映射
    private final Map<String, Boolean> captchaGenerationInProgress = new ConcurrentHashMap<>();

    /**
     * 处理连接建立事件.
     * @param chatSession
     * @param deviceIdAuth
     */
    public void afterConnection(ChatSession chatSession, String deviceIdAuth) {
        final String deviceId = deviceIdAuth;
        final String sessionId = chatSession.getSessionId();
        // 注册会话
        sessionManager.registerSession(sessionId, chatSession);

        logger.info("开始查询设备信息 - DeviceId: {}", deviceId);
        final SysDevice device = Optional.ofNullable(deviceService.selectDeviceById(deviceId)).orElse(new SysDevice());

        // 如果已绑定，则初始化其他内容
        if (!ObjectUtils.isEmpty(device)) {
            device.setDeviceId(deviceId);
            device.setSessionId(sessionId);
            // 更新设备状态
            deviceService.updateNoRefreshCache(new SysDevice()
                    .setDeviceId(device.getDeviceId())
                    .setState(SysDevice.DEVICE_STATE_ONLINE)
                    .setLastLogin(new Date().toString()));
            device.setDeviceId(deviceId);
            device.setSessionId(sessionId);
            sessionManager.registerDevice(sessionId, device);
            //这里需要放在虚拟线程外
            ToolsSessionHolder toolsSessionHolder = new ToolsSessionHolder(chatSession.getSessionId(),
                    device, toolsGlobalRegistry);
            chatSession.setFunctionSessionHolder(toolsSessionHolder);
            //以上同步处理结束后，再启动虚拟线程进行设备初始化，确保chatSession中已设置的sysDevice信息
            Thread.startVirtualThread(() -> {
                try {
                    if (device.getSttId() != null) {
                        SysConfig sttConfig = configManager.getConfig(device.getSttId());
                        if (sttConfig != null) {
                            sttFactory.getSttService(sttConfig);// 提前初始化，加速后续使用
                        }
                    }
                    if (device.getTtsId() != null) {
                        SysConfig ttsConfig = configManager.getConfig(device.getTtsId());
                        if (ttsConfig != null) {// 设备查询从join config表修改为只查设备表，所以这里可能会有空值
                            ttsFactory.getTtsService(ttsConfig, device.getVoiceName());// 提前初始化，加速后续使用
                        }
                    }
                    if (device.getModelId() != null) {
                        chatModelFactory.takeChatModel(device.getModelId());// 提前初始化，加速后续使用
                        chatService.initializeHistory(chatSession);
                        // 注册全局函数
                        toolsSessionHolder.registerGlobalFunctionTools(chatSession);
                    }
                    
                    // 更新设备状态
                    deviceService.updateNoRefreshCache(new SysDevice()
                            .setDeviceId(device.getDeviceId())
                            .setState(SysDevice.DEVICE_STATE_ONLINE)
                            .setLastLogin(new Date().toString()));

                } catch (Exception e) {
                    logger.error("设备初始化失败 - DeviceId: " + deviceId, e);
                    try {
                        sessionManager.closeSession(sessionId);
                    } catch (Exception ex) {
                        logger.error("关闭WebSocket连接失败", ex);
                    }
                }
            });
        }
    }

    /**
     * 处理连接关闭事件.
     * @param sessionId
     */
    public void afterConnectionClosed(String sessionId) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if(chatSession == null || !chatSession.isOpen()){
            return;
        }
        // 连接关闭时清理资源
        SysDevice device = sessionManager.getDeviceConfig(sessionId);
        if (device != null) {
            Thread.startVirtualThread(() -> {
                try {
                    deviceService.updateNoRefreshCache(new SysDevice()
                            .setDeviceId(device.getDeviceId())
                            .setState(SysDevice.DEVICE_STATE_OFFLINE)
                            .setLastLogin(new Date().toString()));
                    logger.info("WebSocket连接关闭 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
                } catch (Exception e) {
                    logger.error("更新设备状态失败", e);
                }
            });
        }
        // 清理会话
        sessionManager.closeSession(sessionId);
        // 清理VAD会话
        vadService.resetSession(sessionId);
        // 清理音频处理会话
        audioService.cleanupSession(sessionId);
        // 清理对话
        dialogueService.cleanupSession(sessionId);
    }

    /**
     * 处理文本消息.
     * @param sessionId
     * @param message
     * @param deviceId 可能为null
     */
    public void handleTextMessage(String sessionId, JsonNode message, String deviceId) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if(chatSession == null || !chatSession.isOpen()){
            return;
        }

        try {
            // 首先尝试解析JSON消息
            String messageType = message.path("type").asText();

            SysDevice device = sessionManager.getDeviceConfig(sessionId);
            // 对于其他消息类型，检查设备是否已绑定
            if (device == null) {
                // 设备信息不存在，需要查询
                Thread.startVirtualThread(() -> {
                    try {
                        SysDevice queryDevice = deviceService.selectDeviceById(deviceId);
                        if (ObjectUtils.isEmpty(queryDevice) || queryDevice.getModelId() == null) {
                            // 设备未绑定，处理未绑定设备的消息
                            queryDevice = new SysDevice();
                            queryDevice.setDeviceId(deviceId);
                            handleUnboundDevice(sessionId, queryDevice);
                        } else {
                            // 更新缓存的设备信息
                            sessionManager.registerDevice(sessionId, queryDevice);
                            // 继续处理消息
                            handleMessageByType(sessionId, message, messageType);
                        }
                    } catch (Exception e) {
                        logger.error("处理消息失败", e);
                    }
                });
            } else if (device.getModelId() == null) {
                // 设备存在但未绑定模型，直接处理未绑定设备
                handleUnboundDevice(sessionId, device);
            } else {
                // 设备已绑定且信息已缓存，直接处理消息
                handleMessageByType(sessionId, message, messageType);
            }
        } catch (Exception e) {
            logger.error("处理文本消息失败", e);
        }
    }

    /**
     * 处理音频数据
     * @param sessionId
     * @param opusData
     */
    public void handleBinaryMessage(String sessionId, byte[] opusData) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if((chatSession == null || !chatSession.isOpen()) && !vadService.isSessionInitialized(sessionId)){
            return;
        }
        // 委托给DialogueService处理音频数据
        dialogueService.processAudioData(chatSession, opusData);

    }

    private void handleMessageByType(String sessionId, JsonNode jsonNode, String messageType) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if(chatSession == null || !chatSession.isOpen()){
            return;
        }       try {
            switch (messageType) {
                case "listen":
                    handleListenMessage(chatSession, jsonNode);
                    break;
                case "abort":
                    dialogueService.abortDialogue(chatSession, jsonNode.path("reason").asText());
                    break;
                case "iot":
                    handleIotMessage(chatSession, jsonNode);
                    break;
                case "goodbye":
                    sessionManager.closeSession(chatSession);
                    break;
                default:
                    logger.warn("未知的消息类型: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("处理消息失败 - 类型: " + messageType, e);
        }
    }

    private void handleUnboundDevice(String sessionId, SysDevice device) {
        String deviceId = device.getDeviceId();
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if(chatSession == null || !chatSession.isOpen()){
            return;
        }
        // 检查是否已经在处理中，使用CAS操作保证线程安全
        Boolean previous = captchaGenerationInProgress.putIfAbsent(deviceId, true);
        if (previous != null && previous) {
            return; // 已经在处理中
        }

        Thread.startVirtualThread(() -> {
            try {
                // 设备已注册但未配置模型
                if (device.getDeviceName() != null && device.getModelId() == null) {
                    String message = "设备未配置对话模型，请到配置页面完成配置后开始对话";

                    String audioFilePath = ttsService.getTtsService().textToSpeech(message);
                    audioService.sendAudioMessage(chatSession, new DialogueService.Sentence(message, audioFilePath), true,
                            true);

                    // 延迟一段时间后再解除标记
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    captchaGenerationInProgress.remove(deviceId);
                    return;
                }

                // 设备未命名，生成验证码
                // 生成新验证码
                SysDevice codeResult = deviceService.generateCode(device);
                String audioFilePath;
                if (!StringUtils.hasText(codeResult.getAudioPath())) {
                    String codeMessage = "请到设备管理页面添加设备，输入验证码" + codeResult.getCode();
                    audioFilePath = ttsService.getTtsService().textToSpeech(codeMessage);
                    codeResult.setDeviceId(deviceId);
                    codeResult.setSessionId(sessionId);
                    codeResult.setAudioPath(audioFilePath);
                    deviceService.updateCode(codeResult);
                } else {
                    audioFilePath = codeResult.getAudioPath();
                }

                audioService.sendAudioMessage(chatSession,
                        new DialogueService.Sentence(codeResult.getCode(), codeResult.getAudioPath()), true, true);

                // 延迟一段时间后再解除标记
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                captchaGenerationInProgress.remove(deviceId);

            } catch (Exception e) {
                logger.error("处理未绑定设备失败", e);
                captchaGenerationInProgress.remove(deviceId);
            }
        });
    }


    private void handleListenMessage(ChatSession chatSession, JsonNode jsonNode) {
        String sessionId = chatSession.getSessionId();
        // 解析listen消息中的state和mode字段
        String state = jsonNode.path("state").asText();
        String mode = jsonNode.path("mode").asText();

        logger.info("收到listen消息 - SessionId: {}, State: {}, Mode: {}", sessionId, state, mode);
        sessionManager.setMode(mode);

        // 根据state处理不同的监听状态
        switch (state) {
            case "start":
                // 开始监听，准备接收音频数据
                logger.info("开始监听 - Mode: {}", mode);

                // 初始化VAD会话
                vadService.initSession(sessionId);
                break;

            case "stop":
                // 停止监听
                logger.info("停止监听");

                // 关闭音频流
                sessionManager.closeAudioStream(sessionId);
                sessionManager.setStreamingState(sessionId, false);
                // 重置VAD会话
                vadService.resetSession(sessionId);
                break;

            case "text":
                // 检测聊天文本输入
                String text = jsonNode.path("text").asText();
                if (audioService.isPlaying(sessionId)) {
                    dialogueService.abortDialogue(chatSession, mode);
                }
                dialogueService.handleText(chatSession, text, null);
                break;

            case "detect":
                // 检测到唤醒词
                String wakeWord = jsonNode.path("text").asText();
                dialogueService.handleWakeWord(chatSession, wakeWord);
                break;

            default:
                logger.warn("未知的listen状态: {}", state);
        }
    }

    private void handleIotMessage(ChatSession chatSession, JsonNode jsonNode) {
        String sessionId = chatSession.getSessionId();
        logger.info("收到IoT消息 - SessionId: {}", sessionId);

        // 处理设备描述信息
        if (jsonNode.has("descriptors")) {
            JsonNode descriptors = jsonNode.path("descriptors");
            logger.info("收到设备描述信息: {}", descriptors);
            // 处理设备描述信息的逻辑
            iotService.handleDeviceDescriptors(sessionId, descriptors);
        }

        // 处理设备状态更新
        if (jsonNode.has("states")) {
            JsonNode states = jsonNode.path("states");
            logger.info("收到设备状态更新: {}", states);
            // 处理设备状态更新的逻辑
            iotService.handleDeviceStates(sessionId, states);
        }
    }
}
