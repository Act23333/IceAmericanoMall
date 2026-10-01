package org.icedamericanomall.auth.infrastructure.adapter.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.RiskControlService;
import org.icedamericanomall.auth.app.port.risk.DeviceTrustService;
import org.icedamericanomall.auth.app.port.risk.LoginFailCountService;
import org.icedamericanomall.auth.app.port.risk.RiskModelClient;
import org.icedamericanomall.auth.app.port.risk.TrustedIpService;
import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 风控评估实现 —— 聚合所有风险维度，输出结构化评估结果。
 *
 * <p>各维度的权重与阈值集中为常量，评分区间映射见 {@link #mapToLevel(int)}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskControlServiceImpl implements RiskControlService {

    // ========== 维度权重 ==========
    private static final int SCORE_BLACKLIST_IP = 50;
    private static final int SCORE_FAIL_COUNT_HIGH = 40;
    private static final int SCORE_FAIL_COUNT_MEDIUM = 20;
    private static final int SCORE_NEW_DEVICE = 15;
    private static final int SCORE_OVERSEAS_IP = 25;
    private static final int SCORE_ABNORMAL_TIME = 10;
    private static final int SCORE_SENSITIVE_OPERATION = 20;
    private static final int SCORE_FIRST_LOGIN = 5;
    private static final int SCORE_MODEL_WEIGHT = 30;

    // ========== 阈值 ==========
    private static final int FAIL_COUNT_VERY_HIGH = 10;
    private static final int FAIL_COUNT_HIGH = 3;
    private static final double MODEL_SCORE_HIGH_RISK = 0.7;

    // ========== 等级阈值 ==========
    private static final int LEVEL_CRITICAL = 80;
    private static final int LEVEL_HIGH = 50;
    private static final int LEVEL_MEDIUM = 25;

    // ========== 异常时间段 ==========
    private static final LocalTime ABNORMAL_TIME_START = LocalTime.of(2, 0);
    private static final LocalTime ABNORMAL_TIME_END = LocalTime.of(5, 0);

    /** 内网网段前缀（简化处理：内网视为本地） */
    private static final String[] INTRANET_PREFIXES = {"192.168.", "10.", "127.0.0."};

    private final TrustedIpService trustedIpService;
    private final LoginFailCountService failCountService;
    private final DeviceTrustService deviceTrustService;
    private final RiskModelClient riskModelClient;

    @Override
    public RiskAssessment assess(LoginSpec spec) {
        Set<RiskTag> tags = new HashSet<>();
        Map<String, Object> details = new HashMap<>();
        int score = 0;

        score += scoreBlacklistIp(spec, tags, details);
        score += scoreFailCount(spec, tags, details);
        score += scoreNewDevice(spec, tags, details);
        score += scoreOverseasIp(spec, tags, details);
        score += scoreAbnormalTime(tags, details);
        score += scoreSensitiveOperation(spec, tags);
        score += scoreFirstLogin(spec, tags);
        score += scoreModel(spec, tags, details);

        // ========== 汇总 ==========
        RiskLevel level = mapToLevel(score);
        details.put("totalScore", score);

        log.info("风控评估: identifier={}, score={}, level={}, tags={}",
                spec.identifier(), score, level, tags);

        return new RiskAssessment(level, tags, details);
    }

    /** 维度 1：IP 黑名单 */
    private int scoreBlacklistIp(LoginSpec spec, Set<RiskTag> tags, Map<String, Object> details) {
        if (!trustedIpService.isBlacklisted(spec.clientIp())) {
            return 0;
        }
        tags.add(RiskTag.BLACKLIST_IP);
        details.put("blacklistIp", true);
        return SCORE_BLACKLIST_IP;
    }

    /** 维度 2：历史失败次数 */
    private int scoreFailCount(LoginSpec spec, Set<RiskTag> tags, Map<String, Object> details) {
        int failCount = failCountService.getFailCount(spec.identifier());
        details.put("failCount", failCount);
        if (failCount >= FAIL_COUNT_VERY_HIGH) {
            tags.add(RiskTag.FAIL_COUNT_HIGH);
            return SCORE_FAIL_COUNT_HIGH;
        }
        if (failCount >= FAIL_COUNT_HIGH) {
            tags.add(RiskTag.FAIL_COUNT_HIGH);
            return SCORE_FAIL_COUNT_MEDIUM;
        }
        return 0;
    }

    /** 维度 3：新设备 */
    private int scoreNewDevice(LoginSpec spec, Set<RiskTag> tags, Map<String, Object> details) {
        boolean newDevice = !deviceTrustService.isTrusted(spec.deviceId());
        details.put("newDevice", newDevice);
        if (!newDevice) {
            return 0;
        }
        tags.add(RiskTag.NEW_DEVICE);
        return SCORE_NEW_DEVICE;
    }

    /** 维度 4：异地登录（简化：非内网视为异地） */
    private int scoreOverseasIp(LoginSpec spec, Set<RiskTag> tags, Map<String, Object> details) {
        boolean overseas = isOverseas(spec.clientIp());
        details.put("overseas", overseas);
        if (!overseas) {
            return 0;
        }
        tags.add(RiskTag.OVERSEAS_IP);
        return SCORE_OVERSEAS_IP;
    }

    /** 维度 5：异常时间段 */
    private int scoreAbnormalTime(Set<RiskTag> tags, Map<String, Object> details) {
        boolean abnormalTime = isAbnormalTime();
        details.put("abnormalTime", abnormalTime);
        if (!abnormalTime) {
            return 0;
        }
        tags.add(RiskTag.ABNORMAL_TIME);
        return SCORE_ABNORMAL_TIME;
    }

    /** 维度 6：敏感操作 */
    private int scoreSensitiveOperation(LoginSpec spec, Set<RiskTag> tags) {
        if (!Boolean.parseBoolean(spec.extra("sensitiveOperation"))) {
            return 0;
        }
        tags.add(RiskTag.SENSITIVE_OPERATION);
        return SCORE_SENSITIVE_OPERATION;
    }

    /** 维度 7：首次登录 */
    private int scoreFirstLogin(LoginSpec spec, Set<RiskTag> tags) {
        if (!Boolean.parseBoolean(spec.extra("firstLogin"))) {
            return 0;
        }
        tags.add(RiskTag.FIRST_LOGIN);
        return SCORE_FIRST_LOGIN;
    }

    /** 维度 8：风控模型评分 */
    private int scoreModel(LoginSpec spec, Set<RiskTag> tags, Map<String, Object> details) {
        double modelScore = riskModelClient.score(spec);
        details.put("modelScore", modelScore);
        if (modelScore >= MODEL_SCORE_HIGH_RISK) {
            tags.add(RiskTag.HIGH_RISK_USER);
        }
        return (int) (modelScore * SCORE_MODEL_WEIGHT);
    }

    private RiskLevel mapToLevel(int score) {
        if (score >= LEVEL_CRITICAL) {
            return RiskLevel.CRITICAL;
        }
        if (score >= LEVEL_HIGH) {
            return RiskLevel.HIGH;
        }
        if (score >= LEVEL_MEDIUM) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private boolean isOverseas(String clientIp) {
        if (clientIp == null) {
            return false;
        }
        for (String prefix : INTRANET_PREFIXES) {
            if (clientIp.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private boolean isAbnormalTime() {
        LocalTime now = LocalTime.now();
        return now.isAfter(ABNORMAL_TIME_START) && now.isBefore(ABNORMAL_TIME_END);
    }
}
