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
      { label: 'Spark', value: 'spark', key: '2' },
      { label: 'Zhipu', value: 'zhipu', key: '3' },
      { label: 'AliYun', value: 'aliyun', key: '4' },
      { label: 'Qwen', value: 'qwen', key: '5' },
      { label: 'Doubao', value: 'doubao', key: '6' },
      { label: 'DeepSeek', value: 'deepseek', key: '7' },
      { label: 'ChatGLM', value: 'chatglm', key: '8' },
      { label: 'Gemini', value: 'gemini', key: '9' },
      { label: 'LMStudio', value: 'lmstudio', key: '10' },
      { label: 'Fastgpt', value: 'fastgpt', key: '11' },
      { label: 'Xinference', value: 'xinference', key: '12' },
    ],
    // 各类别对应的参数字段定义
    typeFields: {
      openai: [
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: '在 https://platform.openai.com/api-keys 申请',
          placeholder: 'sk-...'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions',
          defaultUrl: 'https://api.openai.com/v1',
          help: 'OpenAI官方地址或代理地址',
          placeholder: 'https://api.openai.com/v1'
        },
      ],
      ollama: [
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/api/chat', 
          defaultUrl: "http://localhost:11434",
          help: '本地Ollama服务地址，需要先安装并启动Ollama',
          placeholder: 'http://localhost:11434'
        }
      ],
      spark: [
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 8,
          help: '在 https://console.xfyun.cn/ 申请讯飞开放平台API Key',
          placeholder: 'your-api-key'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://spark-api-open.xf-yun.com/v1",
          help: '讯飞星火大模型API地址',
          placeholder: 'https://spark-api-open.xf-yun.com/v1'
        }
      ],
      zhipu: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://bigmodel.cn/usercenter/proj-mgmt/apikeys 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: 'v4/chat/completions', 
          defaultUrl: "https://open.bigmodel.cn/api/paas",
          help: '智谱AI大模型API地址',
          placeholder: 'https://open.bigmodel.cn/api/paas'
        }
      ],
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
          help: '阿里云通义千问API地址',
          placeholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1'
        }
      ],
      qwen: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
          help: '阿里云通义千问API地址',
          placeholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1'
        }
      ],
      doubao: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey?apikey=%7B%7D 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://ark.cn-beijing.volces.com/api/v3",
          help: '火山引擎豆包大模型API地址',
          placeholder: 'https://ark.cn-beijing.volces.com/api/v3'
        }
      ],
      deepseek: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://platform.deepseek.com/ 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://api.deepseek.com",
          help: 'DeepSeek大模型API地址',
          placeholder: 'https://api.deepseek.com'
        }
      ],
      chatglm: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://bigmodel.cn/usercenter/proj-mgmt/apikeys 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://open.bigmodel.cn/api/paas/v4/",
          help: '智谱AI ChatGLM API地址',
          placeholder: 'https://open.bigmodel.cn/api/paas/v4/'
        }
      ],
      gemini: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://aistudio.google.com/apikey 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "https://generativelanguage.googleapis.com/v1beta/",
          help: 'Google Gemini API地址',
          placeholder: 'https://generativelanguage.googleapis.com/v1beta/'
        }
      ],
      lmstudio: [
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "http://localhost:1234/v1",
          help: '本地LM Studio服务地址，需要先下载并启动LM Studio',
          placeholder: 'http://localhost:1234/v1'
        }
      ],
      fastgpt: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '在 https://cloud.tryfastgpt.ai/account/apikey 申请',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "http://localhost:3000/api/v1",
          help: 'FastGPT API地址，本地部署或云端服务',
          placeholder: 'http://localhost:3000/api/v1'
        }
      ],
      xinference: [
        { 
          name: 'apiKey', 
          label: 'API Secret', 
          required: true, 
          span: 8,
          help: '本地Xinference服务密钥，通常为固定值',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiUrl', 
          label: 'API URL', 
          required: true, 
          span: 12, 
          suffix: '/chat/completions', 
          defaultUrl: "http://localhost:9997/v1",
          help: '本地Xinference服务地址，需要先启动Xinference服务',
          placeholder: 'http://localhost:9997/v1'
        }
      ]
    }
  },
  stt: {
    label: '语音识别',
    typeOptions: [
      { label: 'Tencent', value: 'tencent', key: '0' },
      { label: 'Aliyun', value: 'aliyun', key: '1' },
      { label: 'Xfyun', value: 'xfyun', key: '2' },
      { label: 'FunASR', value: 'funasr', key: '3' }
    ],
    typeFields: {
      tencent: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.cloud.tencent.com/cam/capi 申请',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiKey', 
          label: 'Secret Id', 
          required: true, 
          span: 12,
          help: '腾讯云API密钥ID',
          placeholder: 'your-secret-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Secret Key', 
          required: true, 
          span: 12,
          help: '腾讯云API密钥Key',
          placeholder: 'your-secret-key'
        },
      ],
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'App Key', 
          required: true, 
          span: 12,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-app-key'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.xfyun.cn/ 申请讯飞开放平台AppID',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Secret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Key',
          placeholder: 'your-api-key'
        }
      ],
      funasr: [
        { 
          name: 'apiUrl', 
          label: 'Websocket URL', 
          required: true, 
          span: 12, 
          defaultUrl: "ws://127.0.0.1:10095",
          help: '本地FunASR服务WebSocket地址，需要先部署FunASR服务',
          placeholder: 'ws://127.0.0.1:10095'
        }
      ]
    }
  },
  tts: {
    label: '语音合成',
    typeOptions: [
      { label: 'Aliyun', value: 'aliyun', key: '0' },
      { label: 'Volcengine(doubao)', value: 'volcengine', key: '1' },
      { label: 'Xfyun', value: 'xfyun', key: '2' },
      { label: 'Minimax', value: 'minimax', key: '3' }
    ],
    typeFields: {
      aliyun: [
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: '在 https://bailian.console.aliyun.com/?apiKey=1#/api-key 申请',
          placeholder: 'your-api-key'
        }
      ],
      volcengine: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.volcengine.com/speech/app 申请',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiKey', 
          label: 'Access Token', 
          required: true, 
          span: 12,
          help: '火山引擎语音合成服务访问令牌',
          placeholder: 'your-access-token'
        }
      ],
      xfyun: [
        { 
          name: 'appId', 
          label: 'App Id', 
          required: true, 
          span: 12,
          help: '在 https://console.xfyun.cn/ 申请讯飞开放平台AppID',
          placeholder: 'your-app-id'
        },
        { 
          name: 'apiSecret', 
          label: 'Api Secret', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Secret',
          placeholder: 'your-api-secret'
        },
        { 
          name: 'apiKey', 
          label: 'Api Key', 
          required: true, 
          span: 12,
          help: '讯飞开放平台API Key',
          placeholder: 'your-api-key'
        }
      ],
      minimax: [
        { 
          name: 'appId', 
          label: 'Group Id', 
          required: true, 
          span: 12,
          help: '在 https://platform.minimaxi.com/user-center/basic-information 获取',
          placeholder: 'your-group-id'
        },
        { 
          name: 'apiKey', 
          label: 'API Key', 
          required: true, 
          span: 12,
          help: '在 https://platform.minimaxi.com/user-center/basic-information/interface-key 申请',
          placeholder: 'your-api-key'
        }
      ],
    }
  }
};