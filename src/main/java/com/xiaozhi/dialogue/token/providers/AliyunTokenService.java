package com.xiaozhi.dialogue.token.providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.entity.TokenCache;
import com.xiaozhi.entity.SysConfig;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class AliyunTokenService implements TokenService {

    private static final Logger logger = LoggerFactory.getLogger(AliyunTokenService.class);

    private static final String PROVIDER_NAME = "aliyun";

    private final String ak;
    private final String sk;
    private final Integer configId;

    // Token缓存
    private volatile TokenCache tokenCache;
    // 防止并发刷新的锁
    private final ReentrantLock refreshLock = new ReentrantLock();

    // 阿里云API配置，官方固定值
    private static final String REGIONID = "cn-shanghai";
    private static final String DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com";
    private static final String API_VERSION = "2019-02-28";
    private static final String REQUEST_ACTION = "CreateToken";
    // 响应参数
    private static final String KEY_TOKEN = "Token";
    private static final String KEY_ID = "Id";
    private static final String KEY_EXPIRETIME = "ExpireTime";

    public AliyunTokenService(SysConfig config) {
        this.ak = config.getAk();
        this.sk = config.getSk();
        this.configId = config.getConfigId();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getToken() {
        // 检查缓存是否存在且有效
        if (tokenCache != null) {
            // 更新最后使用时间
            tokenCache.updateLastUsedTime();
            
            // 如果token已过期，清除缓存
            if (tokenCache.isExpired()) {
                clearTokenCache();
            }
            // 如果需要刷新（剩余1小时），异步刷新
            else if (tokenCache.needsRefresh()) {
                refreshTokenAsync();
                return tokenCache.getToken(); // 返回当前还有效的token
            }
            // 如果token仍然有效，直接返回
            else if (isTokenValid()) {
                return tokenCache.getToken();
            }
        }

        // 缓存无效或不存在，获取新token
        return refreshToken();
    }

    @Override
    public String refreshToken() {
        refreshLock.lock();
        try {
            // 双重检查，防止重复刷新
            if (tokenCache != null && isTokenValid() && !tokenCache.needsRefresh()) {
                return tokenCache.getToken();
            }

            // 创建阿里云客户端
            DefaultProfile profile = DefaultProfile.getProfile(REGIONID, ak, sk);
            IAcsClient client = new DefaultAcsClient(profile);
            CommonRequest request = new CommonRequest();
            request.setDomain(DOMAIN);
            request.setVersion(API_VERSION);
            request.setAction(REQUEST_ACTION);
            request.setMethod(MethodType.POST);
            request.setProtocol(ProtocolType.HTTPS);

            // 发送请求
            CommonResponse response = client.getCommonResponse(request);
            
            if (response.getHttpStatus() == 200) {
                // 解析响应
                JSONObject result = JSON.parseObject(response.getData());
                JSONObject tokenObj = result.getJSONObject(KEY_TOKEN);
                String token = tokenObj.getString(KEY_ID);
                long expireTimeSeconds = tokenObj.getLongValue(KEY_EXPIRETIME);
                
                // 转换过期时间
                LocalDateTime expireTime = new Date(expireTimeSeconds * 1000)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                
                // 更新缓存
                tokenCache = new TokenCache(token, expireTime);
                
                return token;
            } else {
                throw new RuntimeException("阿里云API返回错误，HTTP状态码: " + response.getHttpStatus() + 
                                         ", 响应: " + response.getData());
            }
            
        } catch (ClientException e) {
            logger.error("调用阿里云API失败: {}", e.getMessage(), e);
            throw new RuntimeException("调用阿里云API失败: " + e.getMessage(), e);
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public boolean isTokenValid() {
        if (tokenCache == null) {
            return false;
        }
        
        // 检查token是否过期
        return !tokenCache.isExpired();
    }

    @Override
    public void clearTokenCache() {
        tokenCache = null;
    }

    /**
     * 使用虚拟线程异步刷新token
     */
    private void refreshTokenAsync() {
        Thread.startVirtualThread(() -> {
            try {
                refreshToken();
            } catch (Exception e) {
                logger.error("虚拟线程异步刷新Token失败，configId: {}: {}", configId, e.getMessage(), e);
            }
        });
    }

    /**
     * 检查是否需要清除缓存（超过24小时未使用）
     */
    public boolean needsCacheCleanup() {
        return tokenCache != null && tokenCache.needsCacheCleanup();
    }

}
