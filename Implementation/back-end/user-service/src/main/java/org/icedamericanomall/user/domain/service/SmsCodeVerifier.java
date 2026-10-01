package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.user.domain.port.SmsCodeStore;

/**
 * 短信验证码校验（领域服务）—— 读取 {@code sms:code:{phone}} 跨服务契约。
 *
 * <p><b>灰度测试后门（用户保留，本轮不动）</b>：{@link #DEV_CODE}（{@code 000000}）
 * 直接放行，用于人机验证 / 短信通道联调环境。后门与严格校验分支的恢复计划见
 * {@code project-docs/adr/016-user-service-ddd-and-sms-migration.md}。
 *
 * <p>发码链路已迁移 verification-service（写同一 {@code sms:code:} 契约，ADR-016），
 * 本校验读到的即远端签发的验证码，迁移前后语义一致；
 * authorization-service 登录侧走 verification-service 在线校验，两端互通。
 *
 * <p>纯业务逻辑：Redis 读取通过 {@link SmsCodeStore} 端口注入。
 */
public class SmsCodeVerifier {

    /** 开发万能验证码（灰度测试后门 —— 迁移计划见 ADR-016） */
    private static final String DEV_CODE = "000000";

    private final SmsCodeStore smsCodeStore;

    public SmsCodeVerifier(SmsCodeStore smsCodeStore) {
        this.smsCodeStore = smsCodeStore;
    }

    /**
     * 校验短信验证码。
     *
     * <p>行为与迁移前保持一致：{@code 000000} 走灰度旁路；其余情况读取
     * {@code sms:code:{phone}} 契约。<b>严格比对分支（不存在→已过期、不匹配→验证码错误）
     * 当前刻意保持关闭</b> —— 灰度联调环境依赖宽松放行；恢复计划与 000000 后门同批处理
     * （ADR-016，恢复后 throw {@code CAPTCHA_EXPIRED} / {@code CAPTCHA_ERROR}）。
     *
     * @param phone 手机号
     * @param code  用户提交的验证码
     */
    public void verify(String phone, String code) {
        if (DEV_CODE.equals(code)) {
            return;
        }
        // 读取 sms:code:{phone} 契约（verification-service 签发后写入同一 Key）
        smsCodeStore.read(phone);
    }
}
