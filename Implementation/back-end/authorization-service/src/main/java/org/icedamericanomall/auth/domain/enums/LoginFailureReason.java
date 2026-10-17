package org.icedamericanomall.auth.domain.enums;

import lombok.Getter;

/**
 * 登录失败的<b>内部</b>原因分类（2026-10-18 批次 ④，抗账号枚举）。
 *
 * <p>存在的唯一目的：让排障日志能区分「账号不存在 / 密码错 / 状态不可登录」，
 * <b>同时不把这三者泄漏给调用方</b>。对外口径被收敛为同一状态码（401）、
 * 同一业务码（{@code PASSWORD_ERROR}）与同一句中性文案，
 * 详见 {@code ErrorCode#PASSWORD_ERROR} 与 {@code PasswordLoginStrategy} 的类注释。
 *
 * <p><b>硬约束</b>：本枚举<b>不得</b>出现在任何 Response / VO / {@code Result.msg} 中，
 * 只允许写成服务端 WARN 日志的字段。
 */
@Getter
public enum LoginFailureReason {

    /** 该标识在 {@code user_auth} 中不存在（含「标识类型不支持」之外的查无此人） */
    ACCOUNT_NOT_FOUND("账号不存在"),

    /** 账号存在，但提交的密码与存储的哈希不匹配（含凭证行无密码的情况） */
    PASSWORD_MISMATCH("密码不正确"),

    /** 账号存在且密码正确，但状态不可登录（已解绑 / 已锁定） */
    STATUS_NOT_ACTIVE("账号状态不可登录"),

    /** 极高风险命中（失败次数过高 + IP 黑名单），风控层阻断 */
    RISK_BLOCKED("命中风控阻断");

    /** 日志用中文说明（只进服务端日志） */
    private final String desc;

    LoginFailureReason(String desc) {
        this.desc = desc;
    }
}
