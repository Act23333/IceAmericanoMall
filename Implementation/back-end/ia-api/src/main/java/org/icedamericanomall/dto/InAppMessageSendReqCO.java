package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内信发送请求 DTO —— 各服务 → notification-service（Feign）。
 *
 * <p><b>状态：契约预留（V5.0 规划）</b>。站内信的落库、已读回执、未读数统计尚未实现，
 * notification-service 收到本请求会返回「通道未实现」业务异常；调用方应按「可失败的通知」
 * 对待（记录 warn 日志即可，不得影响主流程）。
 *
 * <p>字段沿用评价互动通知的历史结构（item-service 评价回复 / 点赞场景）。
 */
@Data
public class InAppMessageSendReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 接收者用户 ID */
    private Long recipientId;

    /** 触发者用户 ID（系统通知可为空） */
    private Long senderId;

    /** 触发者昵称 */
    private String senderName;

    /** 触发者头像 */
    private String senderAvatar;

    /** 通知类型：REPLY / LIKE / SYSTEM / ORDER ... */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 跳转链接（前端路由） */
    private String linkUrl;
}
