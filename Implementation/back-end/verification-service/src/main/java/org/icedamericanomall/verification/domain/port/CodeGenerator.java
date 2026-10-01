package org.icedamericanomall.verification.domain.port;

/**
 * 验证码生成能力（领域端口）。
 *
 * <p>「生成几位、什么字符集」是业务规则（由 {@code VerificationPolicy} 表达），
 * 「用什么随机算法生成」是技术实现（基础设施层复用 ia-integration 的
 * {@code SmsCodeGenerator}，避免在多个模块重复实现随机数逻辑）。
 */
public interface CodeGenerator {

    /**
     * 生成指定长度的数字验证码（首位非 0）。
     *
     * @param length 位数（4-9）
     * @return 数字验证码字符串
     */
    String generate(int length);
}
