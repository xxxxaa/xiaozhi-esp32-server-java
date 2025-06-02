package com.xiaozhi.communication.common;

import com.xiaozhi.dialogue.iot.IotDescriptor;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ChatSession {
    /**
     * 当前会话的sessionId
     */
    protected String sessionId;
    /**
     * 设备信息
     */
    protected SysDevice sysDevice;
    /**
     * 设备可用角色列表
     */
    protected List<SysRole> sysRoleList;
    /**
     * 设备iot信息
     */
    protected Map<String, IotDescriptor> iotDescriptors = new HashMap<>();
    /**
     * 当前session的function控制器
     */
    protected ToolsSessionHolder toolsSessionHolder;

    /**
     * 当前语音发送完毕后，是否关闭session
     */
    protected boolean closeAfterChat;
    /**
     * 是否正在播放音乐
     */
    protected boolean musicPlaying;
    /**
     * 是否正在说话
     */
    protected boolean playing;
    /**
     * 设备状态（auto, realTime)
     */
    protected String mode;
    /**
     * 会话的音频数据流
     */
    protected Sinks.Many<byte[]> audioSinks;
    /**
     * 会话是否正在进行流式识别
     */
    protected boolean streamingState;
    /**
     * 会话的最后有效活动时间
     */
    protected Instant lastActivityTime;
    /**
     * spring ai 聊天记忆
     */
    protected ChatMemory chatMemory;
    /**
     * 会话属性存储
     */
    protected final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActivityTime = Instant.now();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setDialogueId(String dialogueId){
        setAttribute("currentDialogueId", dialogueId);
    }
    public String getDialogueId(){
        return (String) getAttribute("currentDialogueId");
    }

    public void setUserAudioPath(String userAudioPath){
        setAttribute("userAudioPath_" + getDialogueId(), userAudioPath);
    }
    public String getUserAudioPath(){
        return getDialogueId() == null?"":(String) getAttribute("userAudioPath_" + getDialogueId());
    }

    public void setAssistantAudioPath(String assistantAudioPath) {
        setAttribute( "assistantAudioPath_" + getDialogueId(), assistantAudioPath);
    }
    public String getAssistantAudioPath() {
        String dialogueId = getDialogueId();
        return dialogueId == null ? "": (String) getAttribute("assistantAudioPath_" + dialogueId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public SysDevice getSysDevice() {
        return sysDevice;
    }

    public void setSysDevice(SysDevice sysDevice) {
        this.sysDevice = sysDevice;
    }

    public List<SysRole> getSysRoleList() {
        return sysRoleList;
    }

    public void setSysRoleList(List<SysRole> sysRoleList) {
        this.sysRoleList = sysRoleList;
    }

    public Map<String, IotDescriptor> getIotDescriptors() {
        return iotDescriptors;
    }

    public void setIotDescriptors(Map<String, IotDescriptor> iotDescriptors) {
        this.iotDescriptors = iotDescriptors;
    }

    public ToolsSessionHolder getFunctionSessionHolder() {
        return toolsSessionHolder;
    }

    public void setFunctionSessionHolder(ToolsSessionHolder toolsSessionHolder) {
        this.toolsSessionHolder = toolsSessionHolder;
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolsSessionHolder.getAllFunction();
    }

    public boolean isCloseAfterChat() {
        return closeAfterChat;
    }

    public void setCloseAfterChat(boolean closeAfterChat) {
        this.closeAfterChat = closeAfterChat;
    }

    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    public void setMusicPlaying(boolean musicPlaying) {
        this.musicPlaying = musicPlaying;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Sinks.Many<byte[]> getAudioSinks() {
        return audioSinks;
    }

    public void setAudioSinks(Sinks.Many<byte[]> audioSinks) {
        this.audioSinks = audioSinks;
    }

    public boolean isStreamingState() {
        return streamingState;
    }

    public void setStreamingState(boolean streamingState) {
        this.streamingState = streamingState;
    }

    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(Instant lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public void clearMemory() {
        chatMemory.clear(sessionId);
    }

    public List<Message> getHistoryMessages() {
        return chatMemory.get(sessionId);
    }

    public void addHistoryMessage(Message message){
        chatMemory.add(sessionId, message);
    }
    /**
     * 会话连接是否打开中
     * @return
     */
    public abstract boolean isOpen();
    /**
     * 音频通道是否打开可用
     * @return
     */
    public abstract boolean isAudioChannelOpen();

    public abstract void close();

    public abstract void sendTextMessage(String message);

    public abstract void sendBinaryMessage(byte[] message);
}
