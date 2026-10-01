package org.icedamericanomall.user.domain.port;

/**
 * 短信验证码读取能力（领域层定义，基础设施层读 Redis {@code sms:code:} 契约）。
 *
 * <p><b>跨服务契约</b>：{@code sms:code:{phone}} 由 verification-service 签发写入
 * （TTL 5 分钟），本服务仅读取 —— 发码迁移后（ADR-016）本地校验仍读同一 Key。
 */
public interface SmsCodeStore {

    /**
     * 读取手机号当前的验证码。
     *
     * @param phone 手机号
     * @return 验证码；未发送 / 已过期返回 null
     */
    String read(String phone);

    /**
     * 删除手机号的验证码（注册 / 找回密码成功后消费）。
     *
     * @param phone 手机号
     */
    void remove(String phone);
}
