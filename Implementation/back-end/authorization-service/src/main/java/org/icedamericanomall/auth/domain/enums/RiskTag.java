package org.icedamericanomall.auth.domain.enums;

/**
 * 风险标签 —— 描述"具体命中了什么异常"。
 * <p>与 RiskLevel 组合，支持精细化的处置决策。
 */
public enum RiskTag {

    /** 失败次数过高 */
    FAIL_COUNT_HIGH,

    /** 新设备登录 */
    NEW_DEVICE,

    /** 异地登录 */
    OVERSEAS_IP,

    /** IP 在黑名单中 */
    BLACKLIST_IP,

    /** 异常时间段登录 */
    ABNORMAL_TIME,

    /** 高风险用户（风控模型评分） */
    HIGH_RISK_USER,

    /** 敏感操作（支付、改密） */
    SENSITIVE_OPERATION,

    /** 首次登录 */
    FIRST_LOGIN
}
