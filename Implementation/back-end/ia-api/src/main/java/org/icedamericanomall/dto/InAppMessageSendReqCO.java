package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内信发送请求 DTO —— 各服务 → notification-service（Feign）。
 *
 * <p><b>状态：已实现（ADR-016 收尾）</b>。站内信由 notification-service 落库
 * （{@code notification} 表）；已读回执与未读数经公开端点 {@code /api/notifications/**}
 * 查询维护。发送失败仍按「可失败的通知」对待（记录 warn 日志即可，不得影响主流程）。
 *
 * <p>字段沿用评价互动通知的历史结构（item-service 评价回复 / 点赞场景）。
 */
@Data
public class InAppMessageSendReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 接收者用户 ID */
    @NotNull(message = "接收者不能为空")
    private Long recipientId;

    /** 触发者用户 ID（系统通知可为空） */
    private Long senderId;

    /** 触发者昵称 */
    private String senderName;

    /** 触发者头像 */
    private String senderAvatar;

    /** 通知类型：REPLY / LIKE / SYSTEM / ORDER ... */
    @NotBlank(message = "通知类型不能为空")
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 跳转链接（前端路由） */
    private String linkUrl;
}
