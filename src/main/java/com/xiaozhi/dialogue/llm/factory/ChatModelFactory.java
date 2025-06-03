package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.dialogue.llm.providers.CozeChatModel;
import com.xiaozhi.dialogue.llm.providers.DifyChatModel;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ChatModel工厂
 * 根据配置的模型ID，创建对应的ChatModel
 */
@Component
public class ChatModelFactory {
    @Autowired
    private SysConfigService configService;
    @Autowired
    private ToolCallingManager toolCallingManager;
    private final Logger logger = LoggerFactory.getLogger(ChatModelFactory.class);

    /**
     * 根据配置ID创建ChatModel，首次调用时缓存，缓存key为配置ID。
     * 
     * @see SysConfigService#selectConfigById(Integer) 已经进行了Cacheable,所以此处没有必要缓存
     * @param configId 配置ID，实际是模型ID。
     * @return
     */
    public ChatModel takeChatModel(Integer configId) {
        Assert.notNull(configId, "配置ID不能为空");
        // 根据配置ID查询配置
        SysConfig config = configService.selectConfigById(configId);
        return createChatModel(config);
    }

    /**
     * 创建ChatModel
     * 
     * @param config
     * @return
     */
    private ChatModel createChatModel(SysConfig config) {
        String provider = config.getProvider().toLowerCase();
        String model = config.getConfigName();
        String endpoint = config.getApiUrl();
        String apiKey = config.getApiKey();
        String appId = config.getAppId();
        String apiSecret = config.getApiSecret();
        provider = provider.toLowerCase();
        switch (provider) {
            case "ollama":
                return newOllamaChatModel(endpoint, appId, apiKey, apiSecret, model);
            case "zhipu":
                return newZhipuChatModel(endpoint, appId, apiKey, apiSecret, model);
            case "dify":
                return new DifyChatModel(endpoint, appId, apiKey, apiSecret, model);
            case "coze":
                return new CozeChatModel(endpoint, appId, apiKey, apiSecret, model);
            // 默认为 openai 协议
            default:
                return newOpenAiChatModel(endpoint, appId, apiKey, apiSecret, model);
        }
    }

    private ChatModel newOllamaChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        var ollamaApi = OllamaApi.builder().baseUrl(endpoint).build();

        var chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaOptions.builder()
                                .model(model)
                                .build())
                .build();
        logger.info("Using Ollama model: {}", model);
        return chatModel;
    }

    private ChatModel newOpenAiChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "application/json");

        // LM Studio不支持Http/2，所以需要强制使用HTTP/1.1
        var openAiApi = OpenAiApi.builder()
                .apiKey(StringUtils.hasText(apiKey) ? new SimpleApiKey(apiKey) : new NoopApiKey())
                .baseUrl(endpoint)
                .completionsPath("/chat/completions")
                .headers(headers)
                .webClientBuilder(WebClient.builder()
                        // Force HTTP/1.1 for streaming
                        .clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .restClientBuilder(RestClient.builder()
                        // Force HTTP/1.1 for non-streaming
                        .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .build();
        var openAiChatOptions = OpenAiChatOptions.builder()
                .model(model)
                .build();

        var chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(openAiChatOptions)
                .toolCallingManager(toolCallingManager)
                .build();
        logger.info("Using OpenAi model: {}", model);
        return chatModel;
    }

    private ChatModel newZhipuChatModel(String endpoint, String appId, String apiKey, String apiSecret, String model) {
        var zhiPuAiApi = new ZhiPuAiApi(endpoint, apiKey);

        var chatModel = new ZhiPuAiChatModel(zhiPuAiApi, ZhiPuAiChatOptions.builder()
                .model(model)
                .build());
        logger.info("Using zhiPu model: {}", model);
        return chatModel;
    }
}