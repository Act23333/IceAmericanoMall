package org.icedamericanomall.verification.domain.model;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;

/**
 * 已签发的验证码（领域值对象，不可变 + 创建即校验）。
 *
 * <p>明文验证码只在「签发 → 投递」这段内存路径中流转；存储层由基础设施负责，
 * 且不会通过任何接口返回给调用方（见 {@code VerificationCodeIssueRespCO}）。
 *
 * @param channel 通道
 * @param target  目标（手机号 / 邮箱）
 * @param code    验证码明文
 */
public record VerificationCode(VerificationChannel channel, String target, String code) {

    /** 紧凑构造器：创建即校验。 */
    public VerificationCode {
        if (channel == null) {
            throw new IllegalArgumentException("验证通道不能为空");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("验证目标不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
    }
}
