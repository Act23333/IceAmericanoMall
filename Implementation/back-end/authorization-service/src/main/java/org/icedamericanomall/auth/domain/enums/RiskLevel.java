package org.icedamericanomall.auth.domain.enums;

/**
 * 风险等级。
 * <p>用 severity 数值支持"是否达到阈值"判断，避免 switch 硬编码。
 */
public enum RiskLevel {

    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int severity;

    RiskLevel(int severity) {
        this.severity = severity;
    }

    public boolean isAtLeast(RiskLevel threshold) {
        return this.severity >= threshold.severity;
    }
}
