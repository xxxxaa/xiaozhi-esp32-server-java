/**
 * 系统中各类服务提供商配置
 * 统一管理各类服务的提供商信息，便于维护和扩展
 */

// 配置类型信息映射
export const configTypeMap = {
  llm: {
    label: '模型',
    // 模型类别选项
    typeOptions: [
      { label: 'OpenAI', value: 'openai', key: '0' },
      { label: 'Ollama', value: 'ollama', key: '1' },
      { label: 'Qwen', value: 'qwen', key: '2' },
      { label: 'Spark', value: 'spark', key: '3' }
    ],
    // 各类别对应的参数字段定义
    typeFields: {
      openai: [
        { name: 'apiKey', label: 'API Key', required: true, span: 12 },
        { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/chat/completions' },
      ],
      ollama: [
        { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/api/chat' }
      ],
      qwen: [
        { name: 'apiKey', label: 'API Key', required: true, span: 12 },
        { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/chat/completions' }
      ],
      spark: [
        { name: 'apiSecret', label: 'API Secret', required: true, span: 8 },
        { name: 'apiUrl', label: 'API URL', required: false, span: 12, suffix: '/chat/completions' }
      ]
    }
  },
  stt: {
    label: '语音识别',
    typeOptions: [
      { label: 'Tencent', value: 'tencent', key: '0' },
      { label: 'Aliyun', value: 'aliyun', key: '1' }
    ],
    typeFields: {
      tencent: [
        { name: 'appId', label: 'App Id', required: true, span: 12 },
        { name: 'apiKey', label: 'Secret Id', required: true, span: 12 },
        { name: 'apiSecret', label: 'Secret Key', required: true, span: 12 },
      ],
      aliyun: [
        { name: 'apiKey', label: 'APP Key', required: true, span: 12 },
        { name: 'appId', label: 'Access Key Id', required: true, span: 12 },
        { name: 'apiSecret', label: 'Access Key Secret', required: true, span: 12 }
      ]
    }
  },
  tts: {
    label: '语音合成',
    typeOptions: [
      { label: 'Tencent', value: 'tencent', key: '0' },
      { label: 'Aliyun', value: 'aliyun', key: '1' },
      { label: 'Volcengine(doubao)', value: 'volcengine', key: '2' }
    ],
    typeFields: {
      tencent: [
        { name: 'appId', label: 'App Id', required: true, span: 12 },
        { name: 'apiKey', label: 'Secret Id', required: true, span: 12 },
        { name: 'apiSecret', label: 'Secret Key', required: true, span: 12 },
      ],
      aliyun: [
        { name: 'apiKey', label: 'API Key', required: true, span: 12 },
        { name: 'appId', label: 'Access Key Id', required: true, span: 12 },
        { name: 'apiSecret', label: 'Access Key Secret', required: true, span: 12 }
      ],
      volcengine: [
        { name: 'appId', label: 'App Id', required: true, span: 12 },
        { name: 'apiKey', label: 'Access Token', required: true, span: 12 }
      ]
    }
  }
};