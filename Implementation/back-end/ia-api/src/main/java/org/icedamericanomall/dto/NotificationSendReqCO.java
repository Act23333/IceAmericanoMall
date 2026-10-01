package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通道发送请求 DTO —— 各服务 → notification-service（Feign）。
 *
 * <p>「通道」由端点路径决定（{@code /internal/notify/sms}、{@code /internal/notify/email}），
 * 请求体不再重复携带 channel 字段：避免 body 与路径相互矛盾（不可表达的状态即不存在）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code target}  手机号（SMS）/ 邮箱（EMAIL）</li>
 *   <li>{@code subject} 邮件主题；短信通道忽略</li>
 *   <li>{@code content} 正文内容，调用方负责模板渲染（notification-service 只做通道投递）</li>
 *   <li>{@code requestId} 幂等 / 追踪标识，服务端原样透传，重复投递不报错</li>
 *   <li>{@code scene} 业务场景（LOGIN / REGISTER / ORDER_SHIPPED ...），仅用于日志与限流归因</li>
 * </ul>
 */
@Data
public class NotificationSendReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发送目标：手机号（SMS）或邮箱（EMAIL） */
    @NotBlank(message = "发送目标不能为空")
    private String target;

    /** 邮件主题（短信通道可为空） */
    private String subject;

    /** 正文内容 */
    @NotBlank(message = "发送内容不能为空")
    private String content;

    /** 幂等 / 链路追踪标识（透传，可为空） */
    private String requestId;

    /** 业务场景标识 */
    private String scene;
}
