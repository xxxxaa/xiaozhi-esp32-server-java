package com.xiaozhi.dialogue.llm.api;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/**
 * IotToolCallingManager
 * @author Able
 * 实现物联网设备的工具调用。
 * TODO 后期可以考虑与MCP相结合。
 */
public class IotToolCallingManager implements ToolCallingManager {
    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return List.of();
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        return null;
    }
}
