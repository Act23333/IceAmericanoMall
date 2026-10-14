package org.icedamericanomall.auth.domain.port;

import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;

/**
 * 验证码校验能力（领域层定义，基础设施层调 verification-service 实现）。
 *
 * <p>本端口替代原先的 {@code SmsCodeService}：验证码的<b>签发/存储/校验</b>已收口到
 * verification-service（校验能力唯一出口），auth 上下文不再直读 Redis（解 R5 隐式跨服务契约）。
 *
 * <p>失败语义：校验不通过是<b>预期内业务分支</b>，统一以
 * {@link org.icedamericanomall.common.exception.BizException} 抛出（异常是唯一失败出口），
 * 由基础设施层按远端结果码映射为 {@link org.icedamericanomall.common.enums.ErrorCode}：
 * <ul>
 *   <li>验证码错误 → {@code CAPTCHA_ERROR}</li>
 *   <li>验证码过期/未发送 → {@code CAPTCHA_EXPIRED}</li>
 *   <li>错误次数超限 / 重发过频 → {@code FREQUENT_ERROR}</li>
 *   <li>校验服务不可用（熔断降级）→ {@code SYSTEM_BUSY}，<b>fail-closed</b>：绝不放行</li>
 * </ul>
 */
public interface VerificationCodeService {

    /**
     * 校验并消费验证码（一次性：校验通过即作废，防重放）。
     *
     * @param scene   业务场景（REGISTER / LOGIN / RESET_PASSWORD，参与码池隔离，ADR-051）
     * @param channel 验证通道（短信 / 邮件）
     * @param target  验证码接收目标（手机号 / 邮箱，需与签发时一致）
     * @param code    用户提交的验证码
     * @throws org.icedamericanomall.common.exception.BizException 校验未通过或校验服务不可用
     */
    void verifyAndConsume(VerificationSceneEnum scene, VerificationChannelEnum channel, String target, String code);
}
