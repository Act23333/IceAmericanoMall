package org.icedamericanomall.auth.app.port.risk;

import org.icedamericanomall.auth.domain.model.LoginSpec;

/**
 * 风控模型评分能力（应用层端口，风控维度子端口）。
 */
public interface RiskModelClient {

    /**
     * 对本次登录尝试评分。
     *
     * @param spec 登录尝试
     * @return 风险分值 0.0 ~ 1.0（1.0 为最高风险）
     */
    double score(LoginSpec spec);
}
