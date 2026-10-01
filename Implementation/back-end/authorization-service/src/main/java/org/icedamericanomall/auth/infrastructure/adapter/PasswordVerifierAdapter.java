package org.icedamericanomall.auth.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码哈希校验适配器 —— 用 Spring Security 的 BCrypt 实现领域端口
 * {@link PasswordVerifier}，把技术细节挡在领域层之外。
 */
@Component
@RequiredArgsConstructor
public class PasswordVerifierAdapter implements PasswordVerifier {

    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
