package org.icedamericanomall.auth.domain.ability.risk.impl;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandler;
import org.icedamericanomall.auth.domain.enums.LoginFailureReason;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 极高风险处置：登录阻断（原「账号冻结拦截」）。
 *
 * <p>组合条件：CRITICAL + 失败次数过高 + IP 黑名单 → 直接阻断登录。
 *
 * <p><b>对外口径（2026-10-18 批次 ④）</b>：本处理器属登录链路，阻断时<b>必须</b>走与
 * 「密码错误 / 账号不存在」完全一致的口径 —— {@link ErrorCode#PASSWORD_ERROR}（3007）
 * + HTTP 401 + 中性文案。此前它抛 {@code ACCOUNT_FROZEN(1017)} 且配 HTTP 403，而密码错误是
 * 200、账号不存在是 404：三者状态码互不相同，<b>只看状态码</b>即可判断「这个账号被风控了 /
 * 存在」。原因细分只进服务端 WARN 日志（{@link LoginFailureReason#RISK_BLOCKED}）。
 *
 * <p>若业务上确实需要告知用户「账号已被风控冻结」，只能通过登录<b>成功</b>后的
 * 一次性提示或站内信，<b>不得</b>在登录失败响应里区分。
 */
@Slf4j
public class AccountFreezeHandler implements RiskHandler {

    @Override
    public boolean supports(RiskAssessment assessment) {
        return assessment.level() == RiskLevel.CRITICAL
                && assessment.hasTag(RiskTag.FAIL_COUNT_HIGH)
                && assessment.hasTag(RiskTag.BLACKLIST_IP);
    }

    @Override
    public void handle(RiskAssessment assessment) {
        log.warn("[login][risk][block] reason={}({}), level={}, tags={}",
                LoginFailureReason.RISK_BLOCKED.name(), LoginFailureReason.RISK_BLOCKED.getDesc(),
                assessment.level(), assessment.tags());
        throw new BizException(ErrorCode.PASSWORD_ERROR);
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
