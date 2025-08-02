package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.AjaxResult;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @Author: Joey
 * @Date: 2025/2/28 下午2:46
 * @Description:
 */

@RestController
@RequestMapping("/api/message")
@Tag(name = "消息管理", description = "消息相关操作")
public class MessageController extends BaseController {

    @Resource
    private SysMessageService sysMessageService;

    @Resource
    private ChatService chatService;

    /**
     * 查询对话
     *
     * @param message
     * @return
     */
    @GetMapping("/query")
    @ResponseBody
    @Operation(summary = "根据条件查询对话消息", description = "返回对话消息列表")
    public AjaxResult query(SysMessage message, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            message.setUserId(CmsUtils.getUserId());
            List<SysMessage> messageList = sysMessageService.query(message, pageFilter);
            AjaxResult result = AjaxResult.success();
            result.put("data", new PageInfo<>(messageList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
        }
    }

    /**
     * 删除聊天记录
     * 
     * @param message
     * @return
     */
    @PostMapping("/delete")
    @ResponseBody
    @Operation(summary = "删除对话消息", description = "传递messageId只会删除单条消息，传递deviceId会删除该设备所有消息，逻辑删除")
    public AjaxResult delete(SysMessage message) {
        try {

            message.setUserId(CmsUtils.getUserId());
            int rows = sysMessageService.delete(message);
            if (rows > 0) {
                // 删除聊天记录应该清空当前已建立的对话缓存
                chatService.clearMessageCache(message.getDeviceId());
            }
            return AjaxResult.success();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return AjaxResult.error();
        }
    }
    
}