package org.icedamericanomall.verification.app.port;

import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;

/**
 * 人机验证能力（应用层端口，基础设施层对接具体验证厂商）。
 *
 * <p>端口定义在应用层而非领域层：本服务对人机验证只做「转发 + 判定」，
 * 不含领域规则（验证逻辑属于第三方，风控规则在上游 authorization-service）。
 */
public interface CaptchaVerifyService {

    /**
     * 校验人机验证结果。
     *
     * @param provider 人机验证厂商（GEETEST / ...）
     * @param command  前端提交的验证参数
     * @return true = 验证通过
     */
    boolean verify(String provider, CaptchaVerifyCommand command);
}
