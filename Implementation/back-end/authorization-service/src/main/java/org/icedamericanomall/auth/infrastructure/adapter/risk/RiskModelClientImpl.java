package org.icedamericanomall.auth.infrastructure.adapter.risk;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.risk.RiskModelClient;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.springframework.stereotype.Component;

/**
 * 第三方风控模型客户端（基础设施层）。
 *
 * <p>当前为本地简化评分；接入真实风控服务时替换为 Feign 调用即可，
 * 调用方（{@code RiskControlServiceImpl}）无需改动。
 */
@Slf4j
@Component
public class RiskModelClientImpl implements RiskModelClient {

    /** 缺设备指纹的风险加分 */
    private static final double SCORE_MISSING_DEVICE = 0.3;

    /** 缺客户端 IP 的风险加分 */
    private static final double SCORE_MISSING_IP = 0.2;

    @Override
    public double score(LoginSpec spec) {
        try {
            double score = 0.0;
            if (spec.deviceId() == null) {
                score += SCORE_MISSING_DEVICE;
            }
            if (spec.clientIp() == null) {
                score += SCORE_MISSING_IP;
            }
            return Math.min(score, 1.0);
        } catch (Exception e) {
            // 降级：不阻塞主流程
            log.error("风控模型调用失败，降级为 0", e);
            return 0.0;
        }
    }
}
