package org.icedamericanomall.auth.domain.ability.risk;

import org.icedamericanomall.auth.domain.model.RiskAssessment;

/**
 * 风控处置接口（领域能力）。
 * <p>每种风险处置手段实现这个接口，由 {@link RiskHandlerFactory} 按优先级遍历。
 */
public interface RiskHandler {

    /** 该 Handler 是否处理这个风险评估 */
    boolean supports(RiskAssessment assessment);

    /**
     * 处置逻辑：放行 / 要求人机验证 / 阻断。
     *
     * @param assessment 风险评估结果
     */
    void handle(RiskAssessment assessment);

    /** 优先级（数字越小越优先） */
    default int getOrder() {
        return 100;
    }
}