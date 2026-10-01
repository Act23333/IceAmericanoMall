package org.icedamericanomall.auth.app.port;

import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;

/**
 * 人机验证能力（应用层端口，基础设施层转发至 verification-service）。
 *
 * <p>与旧实现的差异：本上下文不再直连极验（厂商密钥、签名算法、校验地址都不再出现在 auth 配置里），
 * 二次校验统一由 verification-service 的 {@code /internal/verification/captcha/verify} 承担
 * —— 架构归属如此：第三方适配在 ia-integration，校验收口在 verification-service。
 */
public interface CaptchaService {

    /**
     * 人机验证是否已启用。
     *
     * <p>{@code false} = 本环境未接入人机验证（如 dev 未配置极验）。
     * 调用方（应用层）据此<b>跳过</b>校验并打 WARN，不得因为"没有验证能力"而拒绝全部登录。
     *
     * @return true = 已启用，需要按 {@link #verify(CaptchaVerifyParam)} 校验
     */
    boolean isEnabled();

    /**
     * 校验人机验证结果。
     *
     * @param param 前端提交的厂商二次校验参数（含 clientIp）
     * @return true = 验证通过
     * @throws org.icedamericanomall.common.exception.BizException 校验服务不可用（fail-closed，不放行）
     */
    boolean verify(CaptchaVerifyParam param);
}
