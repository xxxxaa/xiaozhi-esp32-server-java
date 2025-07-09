<template>
  <div v-if="visible" class="global-chat-popup">
    <div class="chat-popup-container">
      <!-- 标题栏 -->
      <div 
        class="chat-popup-header"
        @dblclick="handleTitleDoubleClick"
      >
        <div class="header-left">
          <div class="connection-status">
            <div 
              class="status-dot"
              :class="{ 'connected': wsIsConnected, 'disconnected': !wsIsConnected }"
            />
            <span class="status-text">{{ wsConnectionStatus }}</span>
          </div>
        </div>
        
        <div class="header-title">
          <a-icon type="message" />
          <span>小智助手</span>
          <a-tooltip title="双击标题跳转到聊天页面">
            <a-icon type="info-circle" class="info-icon" />
          </a-tooltip>
        </div>
        
        <div class="header-actions">
          <a-button type="text" size="small" @click="handleMinimize">
            <a-icon type="minus" />
          </a-button>
          <a-button type="text" size="small" @click="handleClose">
            <a-icon type="close" />
          </a-button>
        </div>
      </div>
      
      <!-- 聊天内容 -->
      <div class="chat-popup-content">
        <ChatComponent
          ref="chatComponentRef"
          :message-list="messages"
          :show-input="true"
          :show-voice-toggle="true"
          :user-avatar="userAvatar"
          :ai-avatar="aiAvatar"
          :input-placeholder="'输入消息...'"
          :empty-text="'暂无对话，开始聊天吧'"
          :is-connected-prop="wsIsConnected"
          :avatar-size="32"
          :content-max-height="'calc(100% - 80px)'"
          @recording-start="handleRecordingStart"
          @recording-stop="handleRecordingStop"
          @recording-error="handleRecordingError"
          @mode-change="handleModeChange"
        />
      </div>
      
      <!-- 快捷操作 -->
      <div class="chat-popup-footer">
        <a-button 
          type="text" 
          size="small" 
          @click="handleToggleConnection"
          :disabled="wsConnectionStatus === '正在连接...'"
          :loading="wsConnectionStatus === '正在连接...'"
        >
          <a-icon :type="wsIsConnected ? 'disconnect' : 'link'" />
          {{ wsIsConnected ? '断开' : '连接' }}
        </a-button>
        <a-popconfirm
          title="确定要清空所有对话记录吗？"
          ok-text="确定"
          cancel-text="取消"
          placement="top"
          :overlay-style="{ maxWidth: '300px' }"
          @confirm="handleClearMessages"
        >
          <a-button 
            type="text" 
            size="small"
          >
            <a-icon type="delete" />
            清空
          </a-button>
        </a-popconfirm>
        <a-button 
          type="text" 
          size="small" 
          @click="handleGoToFullChat"
        >
          <a-icon type="fullscreen" />
          全屏
        </a-button>
      </div>
    </div>
    
    <!-- 最小化状态 -->
    <div 
      v-if="isMinimized" 
      class="chat-popup-minimized"
      @click="handleRestore"
    >
      <a-icon type="message" />
      <span>小智助手</span>
    </div>
  </div>
</template>

<script>
import ChatComponent from '@/components/ChatComponent'
import { 
  messages,
  clearMessages
} from '@/services/websocketService'
import websocketMixin from '@/mixins/websocketMixin'

export default {
  name: 'GlobalChatPopup',
  mixins: [websocketMixin],
  components: {
    ChatComponent
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      messages: messages,
      isMinimized: false,
      userAvatar: '/assets/user-avatar.png',
      aiAvatar: '/assets/ai-avatar.png'
    }
  },
  watch: {
    visible(newValue) {
      if (newValue) {
        this.isMinimized = false
      }
    }
  },
  methods: {
    // 处理标题双击
    handleTitleDoubleClick() {
      this.$emit('go-to-chat')
    },
    
    // 处理关闭
    handleClose() {
      this.$emit('close')
    },
    
    // 处理最小化
    handleMinimize() {
      this.isMinimized = true
    },
    
    // 处理恢复
    handleRestore() {
      this.isMinimized = false
    },
    
    // 处理切换连接状态 - 使用mixin中的方法
    async handleToggleConnection() {
      if (this.wsIsConnected) {
        await this.disconnectWebSocket()
      } else {
        await this.connectWebSocket()
      }
    },
    
    // 处理清空消息
    handleClearMessages() {
      clearMessages()
      this.$message.success('已清空对话记录')
    },
    
    // 跳转到全屏聊天
    handleGoToFullChat() {
      this.$emit('go-to-chat')
    },
    
    // 处理录音开始
    handleRecordingStart() {
      console.log('录音开始')
    },
    
    // 处理录音结束
    handleRecordingStop() {
      console.log('录音结束')
    },
    
    // 处理录音错误
    handleRecordingError(error) {
      console.error('录音错误:', error)
    },
    
    // 处理模式变化
    handleModeChange(isVoiceMode) {
      console.log('模式变化:', isVoiceMode ? '语音' : '文字')
    },
    
    // 处理WebSocket消息（由mixin调用）
    handleWebSocketMessage(data) {
      console.log('全局聊天框收到消息:', data)
    },
    
    // 处理二进制音频消息
    handleBinaryAudioMessage(audioData) {
      if (this.$refs.chatComponentRef) {
        return this.$refs.chatComponentRef.handleBinaryAudioMessage(audioData)
      }
    }
  }
}
</script>

<style scoped>
.global-chat-popup {
  position: fixed;
  bottom: 90px;
  right: 24px;
  z-index: 1001;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.chat-popup-container {
  width: 380px;
  height: 500px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  border: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  animation: slideInUp 0.3s ease-out;
}

@keyframes slideInUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.chat-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  color: white;
  border-radius: 12px 12px 0 0;
  cursor: pointer;
  user-select: none;
}

.chat-popup-header:hover {
  background: linear-gradient(135deg, #40a9ff, #69c0ff);
}

.header-left {
  display: flex;
  align-items: center;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4d4f;
}

.status-dot.connected {
  background: #52c41a;
}

.status-text {
  font-size: 12px;
  opacity: 0.9;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 16px;
}

.info-icon {
  font-size: 12px;
  opacity: 0.7;
  cursor: help;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-actions .ant-btn {
  color: white;
  border: none;
  background: transparent;
  width: 24px;
  height: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-actions .ant-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.chat-popup-content {
  flex: 1;
  overflow: hidden;
  background: #fafafa;
}

.chat-popup-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #f8f9fa;
  border-top: 1px solid #e8e8e8;
  border-radius: 0 0 12px 12px;
}

.chat-popup-footer .ant-btn {
  color: #666;
  border: none;
  background: transparent;
  font-size: 12px;
  height: 28px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-popup-footer .ant-btn:hover {
  color: #1890ff;
  background: rgba(24, 144, 255, 0.1);
}

.chat-popup-minimized {
  position: absolute;
  bottom: 0;
  right: 0;
  background: #1890ff;
  color: white;
  padding: 8px 16px;
  border-radius: 12px 12px 0 0;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.chat-popup-minimized:hover {
  background: #40a9ff;
  transform: translateY(-2px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-chat-popup {
    bottom: 80px;
    right: 16px;
    left: 16px;
    right: 16px;
  }
  
  .chat-popup-container {
    width: 100%;
    height: 400px;
  }
  
  .header-title {
    font-size: 14px;
  }
  
  .connection-status {
    display: none;
  }
}

/* 暗色主题支持 */
@media (prefers-color-scheme: dark) {
  .chat-popup-container {
    background: #1f1f1f;
    border-color: #333;
  }
  
  .chat-popup-content {
    background: #2a2a2a;
  }
  
  .chat-popup-footer {
    background: #1a1a1a;
    border-color: #333;
  }
}
:deep(.ant-popover.ant-popconfirm) {
  .ant-popover-inner-content {
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
    border: 1px solid #e8e8e8;
  }
  
  .ant-popover-message {
    padding: 12px 16px 8px;
    color: #333;
    font-size: 14px;
    line-height: 1.5;
  }
  
  .ant-popover-message-icon {
    color: #faad14;
    font-size: 16px;
  }
  
  .ant-popover-message-title {
    color: #333;
    font-weight: 500;
  }
  
  .ant-popover-buttons {
    padding: 8px 16px 12px;
    gap: 8px;
  }
  
  .ant-btn {
    border-radius: 6px;
    font-size: 12px;
    height: 28px;
    padding: 0 12px;
    font-weight: 500;
    transition: all 0.2s ease;
  }
  
  .ant-btn-primary {
    background: linear-gradient(135deg, #ff6b6b 0%, #ff5252 100%);
    border: none;
    color: #fff;
    box-shadow: 0 2px 4px rgba(255, 107, 107, 0.3);
  }
  
  .ant-btn-primary:hover {
    background: linear-gradient(135deg, #ff5252 0%, #e53935 100%);
    transform: translateY(-1px);
    box-shadow: 0 4px 8px rgba(255, 107, 107, 0.4);
  }
  
  .ant-btn-default {
    background: #f5f5f5;
    border: 1px solid #d9d9d9;
    color: #666;
  }
  
  .ant-btn-default:hover {
    background: #e8e8e8;
    border-color: #b3b3b3;
    color: #333;
    transform: translateY(-1px);
  }
}
</style> 