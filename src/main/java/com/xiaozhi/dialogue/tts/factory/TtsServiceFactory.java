package com.xiaozhi.dialogue.tts.factory;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.providers.AliyunTtsService;
import com.xiaozhi.dialogue.tts.providers.EdgeTtsService;
import com.xiaozhi.dialogue.tts.providers.VolcengineTtsService;
import com.xiaozhi.entity.SysConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TtsServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceFactory.class);

    // 缓存已初始化的服务：对于API服务，键为"provider:configId"格式；对于本地服务，键为provider名称
    private final Map<String, TtsService> serviceCache = new ConcurrentHashMap<>();

    // 语音生成文件保存地址
    private static final String outputPath = "audio/";

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "edge";

    // 默认 EDGE TTS 服务默认语音名称
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";

    /**
     * 获取默认TTS服务
     */
    public TtsService getDefaultTtsService() {
        // 如果缓存中没有默认服务，则创建一个
        TtsService edgeService = new EdgeTtsService(DEFAULT_VOICE, outputPath);

        return edgeService;
    }

    public TtsService getTtsService() {
        return new EdgeTtsService(DEFAULT_VOICE, outputPath);
    }

    /**
     * 根据配置获取TTS服务
     */
    public TtsService getTtsService(SysConfig config, String voiceName) {

        String provider;
        // 如果提供商为空，则使用默认提供商
        if (ObjectUtils.isEmpty(config)) {
            provider = DEFAULT_PROVIDER;
        } else {
            provider = config.getProvider();
        }
        // 如果是默认提供商且尚未初始化，则初始化
        if (DEFAULT_PROVIDER.equals(provider)) {
            TtsService edgeService = new EdgeTtsService(StringUtils.hasText(voiceName) ? voiceName : DEFAULT_VOICE, outputPath);
            return edgeService;
        }

        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");

        // 检查是否已有该配置的服务实例
        if (serviceCache.containsKey(cacheKey)) {
            return serviceCache.get(cacheKey);
        }

        // 创建新的服务实例
        try {
            TtsService service;
            // 创建其他API服务
            service = createApiService(config, voiceName, outputPath);
            return service;
        } catch (Exception e) {
            logger.error("创建{}服务失败", provider, e);
            return getDefaultTtsService(); // 失败时返回默认服务
        }
    }

    /**
     * 根据配置创建API类型的TTS服务
     */
    private TtsService createApiService(SysConfig config, String voiceName, String outputPath) {
        String provider = config.getProvider();

        // 如果是Edge，直接返回Edge服务
        if (DEFAULT_PROVIDER.equals(provider)) {
            return new EdgeTtsService(voiceName, outputPath);
        } else if ("aliyun".equals(provider)) {
            return new AliyunTtsService(config, voiceName, outputPath);
        } else if ("volcengine".equals(provider)) {
            return new VolcengineTtsService(config, voiceName, outputPath);
        } /*
           * else if ("tencent".equals(provider)) {
           * return new TencentTtsService(config, voiceName, outputPath);
           * }
           */

        logger.warn("不支持的TTS服务提供商: {}", provider);
        return null;
    }

    public void removeCache(SysConfig config) {
        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String provider = config.getProvider();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");
        serviceCache.remove(cacheKey);
    }

}