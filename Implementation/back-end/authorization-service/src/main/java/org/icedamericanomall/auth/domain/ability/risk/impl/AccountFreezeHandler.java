package org.icedamericanomall.auth.domain.ability.risk.impl;

import org.icedamericanomall.auth.domain.ability.risk.RiskHandler;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 极高风险处置：账号冻结拦截。
 *
 * <p>组合条件：CRITICAL + 失败次数过高 + IP 黑名单 → 直接阻断登录。
 */
public class AccountFreezeHandler implements RiskHandler {

    @Override
    public boolean supports(RiskAssessment assessment) {
        return assessment.level() == RiskLevel.CRITICAL
                && assessment.hasTag(RiskTag.FAIL_COUNT_HIGH)
                && assessment.hasTag(RiskTag.BLACKLIST_IP);
    }

    @Override
    public void handle(RiskAssessment assessment) {
        throw new BizException(ErrorCode.ACCOUNT_FROZEN, "账号存在安全风险");
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
