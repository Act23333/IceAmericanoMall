package org.icedamericanomall.verification.infrastructure.adapter;

import org.icedamericanomall.integration.sms.SmsCodeGenerator;
import org.icedamericanomall.verification.domain.port.CodeGenerator;
import org.springframework.stereotype.Component;

/**
 * 验证码生成端口实现（基础设施层）。
 *
 * <p>复用 ia-integration 的 {@link SmsCodeGenerator}（SharedRandom + 首位非 0），
 * 不再在业务模块内重复实现随机数逻辑 —— 这也是「SMS/校验码生成工具」从 ia-common
 * 迁往 ia-integration 后的统一复用点（user-service 直发链路同样使用它）。
 */
@Component
public class CodeGeneratorAdapter implements CodeGenerator {

    @Override
    public String generate(int length) {
        return SmsCodeGenerator.generateCode(length);
    }
}
