package org.icedamericanomall.auth.app.port;

import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;

/**
 * 验证码签发能力（应用层端口，基础设施层转发至 verification-service）。
 *
 * <p>定位说明（与领域端口 {@code VerificationCodeService} 的分工）：
 * <ul>
 *   <li>{@code VerificationCodeService}（domain/port）—— 登录用例内的"校验并消费"，领域策略使用</li>
 *   <li>本端口（app/port）—— 独立的"发码"用例（如邮箱登录发码），应用层编排使用</li>
 * </ul>
 * 两者前者面向领域规则、后者面向用例编排，因此分别声明而不合并。
 */
public interface VerificationCodeIssueService {

    /**
     * 签发并投递验证码（重发限流、有效期、投递失败回滚均由 verification-service 负责）。
     *
     * @param channel 验证通道
     * @param target  接收目标（手机号 / 邮箱，需已归一化）
     * @param scene   业务场景（投递文案与埋点归因）
     * @return 签发结果（有效期秒数）
     * @throws org.icedamericanomall.common.exception.BizException 重发过频 / 投递失败 / 校验服务不可用
     */
    IssuedCodeDTO issueCode(VerificationChannelEnum channel, String target, VerificationSceneEnum scene);
}
