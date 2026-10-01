package org.icedamericanomall.integration.sms;

import java.util.Map;

/**
 * 短信发送客户端接口（第三方集成层）。
 *
 * <p>只暴露「一次投递」的最小语义，Mock / Real 实现由 {@code aliyun.sms.enabled} 切换：
 * {@code false}（默认）→ {@link MockSmsClient} 日志投递；{@code true} → {@link AliyunSmsClient} 真实外呼。
 *
 * <h2>投递失败语义</h2>
 * <b>投递失败必须抛异常</b>（{@code BizException} + {@code ErrorCode.SMS_SEND_FAILED}），
 * 不允许静默吞掉：上游 notification-service 的 {@code /internal/**} 端点会把异常原样透传给
 * Feign 调用方，由调用方决定重试或降级。静默成功会让「没发出去」被当成「已发送」，
 * 验证码场景会直接把用户卡死在登录页。
 *
 * <h2>两套方法（V5.2 新增通用模板参数）</h2>
 * <ul>
 *   <li>{@link #send(String, String)} —— <b>保留</b>的简写形式，第二参即模板变量 {@code code} 的值。
 *       现有实现与调用方（notification-service {@code SmsChannelAdapter}、
 *       user-service {@code SmsServiceImpl}）无需改动。</li>
 *   <li>{@link #send(String, String, Map)} —— 通用形式，可指定<b>模板编码</b>与<b>任意模板变量</b>，
 *       用于「下单成功通知」「物流到达提醒」等模板变量名不是 {@code code} 的场景。</li>
 * </ul>
 */
public interface SmsClient {

    /** 简写形式使用的模板变量名（与阿里云控制台模板中的 {@code ${code}} 对齐）。 */
    String VAR_CODE = "code";

    /**
     * 发送短信（简写形式）：{@code code} 作为模板变量 {@code code} 的值，模板取配置中的默认模板。
     *
     * @param phone 手机号
     * @param code  模板变量 {@code code} 的值（通常是验证码）
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（{@code SMS_SEND_FAILED}）
     */
    void send(String phone, String code);

    /**
     * 发送短信（通用形式）：显式指定模板编码与模板变量表。
     *
     * <p>默认实现<b>向后兼容</b>：委托给 {@link #send(String, String)}，取
     * {@code templateParams} 中的 {@link #VAR_CODE} 变量；此时 {@code templateCode}
     * 不被使用（旧实现只认配置里的默认模板）。仅依赖 {@code code} 变量的自定义实现
     * 因此可以继续工作；需要真正支持多模板的实现（{@link AliyunSmsClient} / {@link MockSmsClient}）
     * 已重写本方法。
     *
     * @param phone          手机号
     * @param templateCode   模板编码；为 {@code null} 时使用实现侧的默认模板
     * @param templateParams 模板变量表（key 必须与短信控制台模板中的 {@code ${key}} 完全一致）
     * @throws org.icedamericanomall.common.exception.BizException 投递失败（{@code SMS_SEND_FAILED}）
     */
    default void send(String phone, String templateCode, Map<String, String> templateParams) {
        send(phone, templateParams == null ? null : templateParams.get(VAR_CODE));
    }
}
