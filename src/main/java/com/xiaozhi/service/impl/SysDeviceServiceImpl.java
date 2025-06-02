package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.ConfigManager;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.dao.DeviceMapper;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysDeviceService;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * 设备操作
 *
 * @author Joey
 *
 */

@Service
public class SysDeviceServiceImpl extends BaseServiceImpl implements SysDeviceService {
    private static final Logger logger = LoggerFactory.getLogger(SysDeviceServiceImpl.class);

    private final static String CACHE_NAME = "XiaoZhi:SysDevice";

    @Resource
    private DeviceMapper deviceMapper;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private ConfigMapper configMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private SysConfigService configService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private ConfigManager configManager;

    /**
     * 添加设备
     *
     * @param device
     * @return
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public int add(SysDevice device) {

        SysDevice existingDevice = deviceMapper.selectDeviceById(device.getDeviceId(), false, false);
        if (existingDevice != null) {
            return 1;
        }

        // 先查询是否有默认配置
        SysConfig queryConfig = new SysConfig();
        queryConfig.setUserId(device.getUserId());
        queryConfig.setIsDefault("1");
        // 该配置中不包含 coze 智能体
        List<SysConfig> configs = configMapper.query(queryConfig);
        // 单独查询智能体
        List<SysConfig> cozeAgents = configMapper.query(queryConfig.setProvider("coze").setConfigType("llm"));
        List<SysConfig> difyAgents = configMapper.query(queryConfig.setProvider("dify").setConfigType("llm"));

        // 合并去重，按照 configId 去重
        List<SysConfig> mergedConfigs = new ArrayList<>();

        // 使用LinkedHashMap保持插入顺序，以configId为键去重
        Map<Integer, SysConfig> configMap = new LinkedHashMap<>();

        // 先添加configs中的配置
        for (SysConfig config : configs) {
            configMap.put(config.getConfigId(), config);
        }

        // 添加cozeAgents中的配置
        for (SysConfig agent : cozeAgents) {
            configMap.put(agent.getConfigId(), agent);
        }

        // 添加difyAgents中的配置
        for (SysConfig agent : difyAgents) {
            configMap.put(agent.getConfigId(), agent);
        }
        // 转换回List
        mergedConfigs = new ArrayList<>(configMap.values());

        // 遍历查询是否有默认配置
        for (SysConfig config : mergedConfigs) {
            if (config.getConfigType().equals("llm")) {
                device.setModelId(config.getConfigId());
            } else if (config.getConfigType().equals("stt")) {
                device.setSttId(config.getConfigId());
            }
        }

        // 查询是否有默认角色
        SysRole queryRole = new SysRole();
        queryRole.setUserId(device.getUserId());
        queryRole.setIsDefault("1");
        List<SysRole> roles = roleMapper.query(queryRole);

        if (roles.size() > 0) {
            device.setRoleId(roles.get(0).getRoleId());
        }
        // 添加设备
        return deviceMapper.add(device);
    }

    /**
     * 删除设备
     *
     * @param device
     * @return
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    @CacheEvict(value = CACHE_NAME, key = "#device.deviceId.replace(\":\", \"-\")")
    public int delete(SysDevice device) {
        int row = deviceMapper.delete(device);
        if (row > 0) {
            SysMessage message = new SysMessage();
            message.setUserId(device.getUserId());
            message.setDeviceId(device.getDeviceId());
            // 清空设备聊天记录
            messageMapper.delete(message);
        }
        return row;
    }

    /**
     * 查询设备信息
     *
     * @param device
     * @return
     */
    @Override
    public List<SysDevice> query(SysDevice device, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return deviceMapper.query(device);
    }

    @Override
    @Cacheable(value = CACHE_NAME, key = "#deviceId.replace(\":\", \"-\")", unless = "#result == null")
    public SysDevice selectDeviceById(String deviceId) {
        return deviceMapper.selectDeviceById(deviceId, false, false);
    }

    /**
     * 查询验证码
     */
    @Override
    public SysDevice queryVerifyCode(SysDevice device) {
        return deviceMapper.queryVerifyCode(device);
    }

    /**
     * 查询并生成验证码
     * 
     */
    @Override
    public SysDevice generateCode(SysDevice device) {
        SysDevice result = deviceMapper.queryVerifyCode(device);
        if (result == null) {
            result = new SysDevice();
            deviceMapper.generateCode(device);
            result.setCode(device.getCode());
        }
        return result;
    }

    /**
     * 关系设备验证码语音路径
     */
    @Override
    public int updateCode(SysDevice device) {
        return deviceMapper.updateCode(device);
    }

    /**
     * 更新设备信息
     *
     * @param device
     * @return
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    @CachePut(value = CACHE_NAME, key = "#device.deviceId.replace(\":\", \"-\")", unless = "#result == null")
    public SysDevice update(SysDevice device) {
        int count = updateNoRefreshCache(device);
        if (count > 0) {
            return deviceMapper.selectDeviceById(device.getDeviceId(), false, false);
        } else {
            return null;
        }
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public int updateNoRefreshCache(SysDevice device) {
        if (!ObjectUtils.isEmpty(device.getRoleId())) {
            SysRole role = roleMapper.selectRoleById(device.getRoleId());
            if (role != null) {
                List<SysDevice> currentDevices = deviceMapper.query(device);
                if (currentDevices != null && !currentDevices.isEmpty()) {
                    SysDevice currentDevice = currentDevices.get(0);
                    // 如果当前设备角色和修改的角色不一致，需要清空聊天记录
                    if (currentDevice.getRoleId() != null && !currentDevice.getRoleId().equals(role.getRoleId())) {
                        SysMessage message = new SysMessage();
                        message.setUserId(device.getUserId());
                        message.setDeviceId(device.getDeviceId());
                        // 清空设备聊天记录
                        messageMapper.delete(message);
                    }
                }
            }
        }
        return deviceMapper.update(device);
    }

    @Override
    public void refreshSessionConfig(SysDevice device) {
        try {
            String deviceId = device.getDeviceId();
            ChatSession session = sessionManager.getSessionByDeviceId(deviceId);

            if (session != null) {
                SysDevice updateDevice = device;
                updateDevice.setSessionId(session.getSessionId());
                SysRole roleConfig = new SysRole();
                // 通过roleId获取ttsId
                if (device.getRoleId() != null) {
                    roleConfig = roleMapper.selectRoleById(device.getRoleId());
                }
                if (device.getModelId() != null) {
                    updateDevice.setModelId(device.getModelId());
                }
                if (device.getSttId() != null) {
                    updateDevice.setSttId(device.getSttId());
                    if (device.getSttId() != -1) {
                        configManager.getConfig(device.getSttId());
                    }
                }
                if (roleConfig.getTtsId() != null) {
                    updateDevice.setTtsId(roleConfig.getTtsId());
                    if (device.getTtsId() != -1) {
                        configManager.getConfig(roleConfig.getTtsId());
                        updateDevice.setVoiceName(roleConfig.getVoiceName());
                    }
                }
                // 更新配置信息
                session.setSysDevice(updateDevice);
            }
        } catch (Exception e) {
            logger.error("刷新设备会话配置时发生错误", e);
        }
    }

    @Override
    public String generateToken(String deviceId) {
        String token = UUID.randomUUID().toString();
        deviceMapper.insertCode(deviceId, token);
        return token;
    }

}