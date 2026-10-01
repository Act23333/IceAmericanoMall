package org.icedamericanomall.auth.app.port;

import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.RiskAssessment;

/**
 * 风控评估服务（应用层端口，基础设施层聚合各风险维度）。
 *
 * <p>只有应用层调用，所以放在应用层 Port；入参出参均为领域对象。
 */
public interface RiskControlService {

    /**
     * 评估登录风险，返回完整的评估结果。
     *
     * @param spec 登录尝试
     * @return 风险评估（等级 + 标签 + 明细）
     */
    RiskAssessment assess(LoginSpec spec);
}
