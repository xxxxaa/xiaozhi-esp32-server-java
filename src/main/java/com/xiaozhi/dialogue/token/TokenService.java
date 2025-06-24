package com.xiaozhi.dialogue.token;

public interface TokenService {
    /**
     * 获取Token (统一入口)
     */
    String getToken();

    /**
     * 获取服务提供商名称
     */
    String getProviderName();
    
    /**
     * 手动刷新Token
     */
    String refreshToken();
    
    /**
     * 检查Token是否有效
     */
    boolean isTokenValid();
    
    /**
     * 清理指定配置的Token缓存
     */
    void clearTokenCache();
}
