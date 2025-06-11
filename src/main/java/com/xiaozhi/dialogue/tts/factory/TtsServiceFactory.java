package com.xiaozhi.dialogue.tts.factory;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.providers.*;
import com.xiaozhi.entity.SysConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TtsServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceFactory.class);

    // 缓存已初始化的服务：对于API服务，键为"provider:configId"格式；对于本地服务，键为provider名称
    private final Map<String, TtsService> serviceCache = new ConcurrentHashMap<>();

    // 语音生成文件保存地址
    private static final String OUT_PUT_PATH = "audio/";

    // 默认服务提供商名称
    private static final String DEFAULT_PROVIDER = "edge";

    // 默认 EDGE TTS 服务默认语音名称
    private static final String DEFAULT_VOICE = "zh-CN-XiaoyiNeural";

    /**
     * 获取默认TTS服务
     */
    public TtsService getDefaultTtsService() {
        var config = new SysConfig().setProvider(DEFAULT_PROVIDER);
        return getTtsService(config, TtsServiceFactory.DEFAULT_VOICE);
    }

    // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
    private String createCacheKey(SysConfig config, String provider) {
        var configId = config.getConfigId();
        var configIdStr = configId != null ? String.valueOf(configId) : "default";
        return provider + ":" + configIdStr;
    }

    /**
     * 根据配置获取TTS服务
     */
    public TtsService getTtsService(SysConfig config, String voiceName) {
        // 如果提供商为空，则使用默认提供商
        var provider = ObjectUtils.isEmpty(config) ? DEFAULT_PROVIDER : config.getProvider();
        var cacheKey = createCacheKey(config, provider);

        // 检查是否已有该配置的服务实例
        if (serviceCache.containsKey(cacheKey)) {
            return serviceCache.get(cacheKey);
        }

        var service = createApiService(config, voiceName, OUT_PUT_PATH);
        serviceCache.put(cacheKey, service);
        return service;
    }

    /**
     * 根据配置创建API类型的TTS服务
     */
    private TtsService createApiService(SysConfig config, String voiceName, String outputPath) {
        // Make sure output dir exists
        ensureOutputPath(outputPath);

        return switch (config.getProvider()) {
            case "aliyun" -> new AliyunTtsService(config, voiceName, outputPath);
            case "volcengine" -> new VolcengineTtsService(config, voiceName, outputPath);
            case "xfyun" -> new XfyunTtsService(config, voiceName, outputPath);
            case "minimax" -> new MiniMaxTtsService(config, voiceName, outputPath);
            default -> new EdgeTtsService(voiceName, outputPath);
        };
    }

    public void removeCache(SysConfig config) {
        // 对于API服务，使用"provider:configId"作为缓存键，确保每个配置使用独立的服务实例
        Integer configId = config.getConfigId();
        String provider = config.getProvider();
        String cacheKey = provider + ":" + (configId != null ? configId : "default");
        serviceCache.remove(cacheKey);
    }

    private void ensureOutputPath(String outputPath) {
        File dir = new File(outputPath);
        if (!dir.exists()) dir.mkdirs();
    }
}