package org.icedamericanomall.auth.adapter.web;

import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWKS 端点（适配层）—— 对外暴露公钥，供网关 / 资源服务验签 JWT。
 *
 * <p>说明：本端点<b>有意</b>不包装 {@code Result}：JWKS 是 OAuth2 标准协议端点，
 * 消费者（网关、Nimbus 客户端库）要求形如 {@code {"keys":[...]}} 的原始 JSON。
 */
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class JwkSetController {

    private final JWKSet jwkSet;

    /**
     * 获取 JWK Set。
     *
     * @return {@code {"keys": [...]}} 结构（密钥轮换期间会同时包含新旧公钥）
     */
    @GetMapping("/jwks")
    public Map<String, Object> getJwks() {
        return jwkSet.toJSONObject();
    }
}
