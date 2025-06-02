package com.xiaozhi.dialogue.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.iot.IotDescriptor;
import com.xiaozhi.dialogue.iot.IotMethod;
import com.xiaozhi.dialogue.iot.IotMethodParameter;
import com.xiaozhi.dialogue.iot.IotProperty;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Iot服务 - 负责iot处理和WebSocket发送
 */
@Service
public class IotService {
    private static final Logger logger = LoggerFactory.getLogger(IotService.class);
    private static final String TAG = "IotService";

    @Resource
    private SessionManager sessionManager;

    @Resource
    private MessageService messageService;

    /**
     * 处理iot设备描述信息，形成function_call，注册进sessionManager，用于后续的llm调用及设备调用
     *
     * @param sessionId 会话ID
     * @param descriptors  iot设备描述消息内容
     */
    public void handleDeviceDescriptors(String sessionId, JsonNode descriptors) {
        Iterator<JsonNode> iotDescriptorIterator = descriptors.elements();
        while (iotDescriptorIterator.hasNext()) {
            JsonNode iotDescriptorJson = iotDescriptorIterator.next();

            String iotName = iotDescriptorJson.path("name").asText();
            String description = iotDescriptorJson.path("description").asText();
            JsonNode properties = iotDescriptorJson.path("properties");
            JsonNode methods = iotDescriptorJson.path("methods");
            if(properties.isMissingNode() && methods.isMissingNode()) {
                return;
            }
            // 记录iot设备描述信息
            IotDescriptor iotDescriptor = new IotDescriptor(
                    iotName,
                    description,
                    properties,
                    methods
            );
            sessionManager.registerIot(sessionId, iotDescriptor);
            registerFunctionTools(sessionId, iotDescriptor);
        }
    }

    /**
     * 处理iot设备状态变更信息，可用于更新设备状态或进行其他操作
     *
     * @param sessionId 会话ID
     * @param states  iot状体消息内容
     */
    public void handleDeviceStates(String sessionId, JsonNode states) {
        Iterator<JsonNode> iotStateIterator = states.elements();

        while (iotStateIterator.hasNext()) {
            JsonNode iotState = iotStateIterator.next();

            String iotName = iotState.path("name").asText();
            IotDescriptor iotDescriptor = sessionManager.getIotDescriptor(sessionId, iotName);
            if (iotDescriptor == null) {
                logger.error("[{}] - SessionId: {} 未找到设备: {} 的描述信息", TAG, sessionId, iotName);
                continue;
            }
            JsonNode iotStateValues = iotState.path("state");
            Iterator<String> stateIterator = iotStateValues.fieldNames();
            while (stateIterator.hasNext()) {
                String propertyName = stateIterator.next();
                //获取新的属性值
                JsonNode propertyValue = iotStateValues.path(propertyName);

                IotProperty property = iotDescriptor.getProperties().get(propertyName);
                if (property != null) {
                    // 类型检查
                    String newValueType = propertyValue.getNodeType().toString();
                    if (!property.getType().equalsIgnoreCase(newValueType)) {
                        logger.error("[{}] - SessionId: {} handleDeviceStates 属性: {} 的值类型不匹配, 注册类型: {}, 入参类型: {}", TAG, sessionId, propertyName, property.getType(), newValueType);
                        continue;
                    }
                    property.setValue(propertyValue);
                    logger.info("[{}] - SessionId: {} handleDeviceStates 物联网状态更新: {} , {} = {}", TAG, sessionId, iotName, propertyName, propertyValue);
                }else{
                    logger.error("[{}] - SessionId: {} handleDeviceStates 未找到设备 {} 的属性 {}", TAG, sessionId, iotName, propertyName);
                }
            }
        }
    }

    /**
     * 获取物联网状态
     *
     * @param sessionId 会话ID
     * @param iotName iot设备名称
     * @param propertyName 属性名称
     * @return 属性值，如未找到则返回null
     */
    public Object getIotStatus(String sessionId, String iotName, String propertyName) {
        IotDescriptor iotDescriptor = sessionManager.getIotDescriptor(sessionId, iotName);

        if (iotDescriptor != null) {
            IotProperty property = iotDescriptor.getProperties().get(propertyName);
            if (property != null) {
                return property.getValue();
            }else{
                logger.error("[{}] - SessionId: {} getIotStatus 未找到设备 {} 的属性 {}", TAG, sessionId, iotName, propertyName);
            }
        }else{
            logger.error("[{}] - SessionId: {} getIotStatus 未找到设备 {}", TAG, sessionId, iotName);
        }
        return null;
    }

    /**
     * 设置物联网状态
     *
     * @param sessionId 会话ID
     * @param iotName iot设备名称
     * @param propertyName 属性名称
     * @param value 属性值
     * @return 是否设置成功
     */
    public boolean setIotStatus(String sessionId, String iotName, String propertyName, Object value) {
        IotDescriptor iotDescriptor = sessionManager.getIotDescriptor(sessionId, iotName);

        if (iotDescriptor != null) {
            IotProperty property = iotDescriptor.getProperties().get(propertyName);
            if (property != null) {
                // 类型检查
                boolean typeCheck = false;
                if(property.getType().equalsIgnoreCase(JsonNodeType.OBJECT.name())){
                    typeCheck = true;
                }else if(value instanceof Number && property.getType().equalsIgnoreCase(JsonNodeType.NUMBER.name())){
                    typeCheck = true;
                }else if(value instanceof String && property.getType().equalsIgnoreCase(JsonNodeType.STRING.name())){
                    typeCheck = true;
                }else if(value instanceof Boolean && property.getType().equalsIgnoreCase(JsonNodeType.BOOLEAN.name())){
                    typeCheck = true;
                }
                if (!typeCheck) {
                    logger.error("[{}] - SessionId: {} setIotStatus 属性: {} 的值类型不匹配, 注册类型: {}, 入参类型: {}", TAG, sessionId, propertyName,
                            property.getType(), value.getClass().getSimpleName());
                    return false;
                }
                property.setValue(value);
                logger.info("[{}] - SessionId: {} setIotStatus 物联网状态更新: {} , {} = {}", TAG, sessionId, iotName, propertyName, value);
                sendIotMessage(sessionId, iotName, propertyName, Collections.singletonMap(propertyName, value));
                return true;
            }
        }
        logger.error("[{}] - SessionId: {} setIotStatus 未找到设备 {} 的属性 {}", TAG, sessionId, iotName, propertyName);
        return false;
    }

    /**
     * 发送iot消息到设备
     *
     * @param sessionId 会话ID
     * @param iotName   iot设备名称
     * @param methodName 方法名称
     * @param parameters 方法参数
     */
    public boolean sendIotMessage(String sessionId, String iotName, String methodName, Map<String, Object> parameters) {
        try {
            logger.info("[{}] - SessionId: {}, message send iotName: {}, methodName: {}, parameters: {}", TAG, sessionId,
                    iotName, methodName, JsonUtil.toJson(parameters));
            ChatSession session = sessionManager.getSession(sessionId);
            IotDescriptor iotDescriptor = sessionManager.getIotDescriptor(sessionId, iotName);
            if(iotDescriptor != null && iotDescriptor.getMethods().containsKey(methodName)){
                Map<String, Object> command = new HashMap<>();
                command.put("name", iotName);
                command.put("method", methodName);
                command.put("parameters", parameters);
                messageService.sendIotCommandMessage(session, Collections.singletonList(command));
                return true;
            }else{
                logger.error("[{}] - SessionId: {}, {} method not found: {}", TAG, sessionId, iotName, methodName);
            }
        } catch (Exception e) {
            logger.error("[{}] - SessionId: {}, error sending Iot message", TAG, sessionId, e);
        }
        return false;
    }
    /**
     * 注册iot设备的函数到FunctionHolder
     *
     * @param sessionId 会话ID
     * @param iotDescriptor  session绑定的FunctionHolder
     */
    private void registerFunctionTools(String sessionId, IotDescriptor iotDescriptor) {
        ToolsSessionHolder toolsSessionHolder = sessionManager.getFunctionSessionHolder(sessionId);
        registerPropertiesFunctionTools(sessionId, toolsSessionHolder, iotDescriptor);
        registerMethodFunctionTools(sessionId, toolsSessionHolder, iotDescriptor);
    }

    /**
     * 注册iot设备的属性的查询方法到FunctionHolder
     *
     * @param sessionId 会话ID
     * @param toolsSessionHolder session绑定的FunctionHolder
     * @param iotDescriptor  iot信息
     */
    private void registerPropertiesFunctionTools(String sessionId, ToolsSessionHolder toolsSessionHolder, IotDescriptor iotDescriptor) {
        //遍历properties，生成FunctionCallTool
        String iotName = iotDescriptor.getName();
        for (IotProperty propInfo : iotDescriptor.getProperties().values()) {
            String propName = propInfo.getName();
            // 创建函数名称，格式：get_{属性名称}
            String funcName = "iot_get_" + iotName.toLowerCase() + "_" + propName.toLowerCase();
            ToolCallback toolCallback = FunctionToolCallback
                    .builder(funcName, (Map<String, String> params, ToolContext toolContext) -> {
                        Object value = getIotStatus(sessionId, iotName, propName);
                        if (value != null) {
                            // 获取参数
                            String response_success = params.get("response_success");
                            //如果有success参数，并且有{value}占位符，用相关参数替换
                            if (response_success != null) {
                                if(response_success.contains("{value}")){
                                    response_success = response_success.replace("{value}", String.valueOf(value));
                                }
                            }else{
                                response_success = "当前的设置为" + value;
                            }
                            return response_success;
                        } else {
                            return "无法获取设置";
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                    .description("查询" + iotName + "的" + propInfo.getDescription())
                    .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "response_success": {
                                    "type": "string",
                                    "description": "查询成功时的友好回复，必须使用{value}作为占位符表示查询到的值"
                                }
                            },
                            "required": ["response_success"]
                        }
                    """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
            // 注册到当前会话的函数持有者
            toolsSessionHolder.registerFunction(funcName, toolCallback);
        }

    }

    /**
     * 注册iot设备的可调用方法到FunctionHolder
     *
     * @param sessionId 会话ID
     * @param toolsSessionHolder FunctionHolder实例
     * @param iotDescriptor  iot信息
     */
    private void registerMethodFunctionTools(String sessionId, ToolsSessionHolder toolsSessionHolder, IotDescriptor iotDescriptor) {
        // 遍历methods，生成FunctionCallTool
        String iotName = iotDescriptor.getName();

        for (IotMethod iotMethod : iotDescriptor.getMethods().values()) {
            // 创建函数名称，格式：iot_{iotName}_{methodName}
            String funcName = "iot_" + iotMethod.getName();

            Map<String, String> valueMap = new HashMap<>();
            //获取iotMethod方法参数，添加到函数参数中。 iot方法都是单参数
            for (IotMethodParameter iotMethodParameter : iotMethod.getParameters().values()) {
                valueMap.put("paramName", iotMethodParameter.getName());
                valueMap.put("paramType", iotMethodParameter.getType());
                valueMap.put("paramDescription", iotMethodParameter.getDescription());
            }
            String inputSchema = StringSubstitutor.replace("""
                        {
                            "type": "object",
                            "properties": {
                                "${paramName}": {
                                    "type": "${paramType}",
                                    "description": "${paramDescription}"
                                },
                                "response_success": {
                                    "type": "string",
                                    "description": "操作成功时的友好回复,关于该设备的操作结果，设备名称使用description中的名称，不要出现占位符"
                                }
                            },
                            "required": ["${paramName}", "response_success"]
                        }
                    """, valueMap);

            ToolCallback toolCallback = FunctionToolCallback
                    .builder(funcName, (Map<String, Object> params, ToolContext toolContext) -> {
                        String actFuncName = funcName.substring(4); // 原始方法调用，去掉iot_前缀
                        boolean result = sendIotMessage(sessionId, iotName, actFuncName, params);
                        if (result) {
                            // 获取参数
                            String response_success = (String)params.get("response_success");
                            if (response_success == null || response_success.isEmpty()) {
                                response_success = "操作成功";
                            }
                            return response_success;
                        } else {
                            return "操作失败";
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                    .description(iotDescriptor.getDescription() + " - " + iotMethod.getDescription())
                    .inputSchema(inputSchema)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
            // 注册到当前会话的函数持有者
            toolsSessionHolder.registerFunction(funcName, toolCallback);
        }
    }

}
