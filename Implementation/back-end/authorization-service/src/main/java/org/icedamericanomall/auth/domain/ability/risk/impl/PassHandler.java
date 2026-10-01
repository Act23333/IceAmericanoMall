package org.icedamericanomall.auth.domain.ability.risk.impl;

import org.icedamericanomall.auth.domain.ability.risk.RiskHandler;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.model.RiskAssessment;

/**
 * 低风险处置：放行。
 */
public class PassHandler implements RiskHandler {

    @Override
    public boolean supports(RiskAssessment assessment) {
        return assessment.level() == RiskLevel.LOW;
    }

    @Override
    public void handle(RiskAssessment assessment) {
        // 低风险：放行，不做任何处置
    }

    @Override
    public int getOrder() {
        return 4;
    }
}
