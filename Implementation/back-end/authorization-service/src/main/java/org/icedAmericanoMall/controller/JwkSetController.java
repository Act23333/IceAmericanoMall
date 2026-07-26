package org.icedAmericanoMall.controller;

import com.nimbusds.jose.jwk.JWKSet;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class JwkSetController {
    private final JWKSet jwkSet;
    @GetMapping("/jwks")
    public Map<String, Object> getJwks() {
        return jwkSet.toJSONObject();
    }
    //jwkSource：是一个 JWKSource<SecurityContext> 对象，它内部持有认证服务器的 RSA 密钥对（私钥用于签名 JWT，公钥用于验证）。在您的代码中，jwkSource 通过 JwtConfig 配置生成，加载了 PKCS#12 密钥库中的私钥和证书，并构建了一个包含公钥信息的 RSAKey 对象。
    //private final JWKSource<SecurityContext> jwkSource;

//    @GetMapping("/jwks")
//    public Map<String, Object> getJwks() throws Exception {
////        JWKSet jwkSet = jwkSource.get(new JWSKeySelectorSecurityContext());
//        //Lambda 参数 (jwkSelector, context)：jwkSelector 是 JWKSelector 类型，用于从 JWKSource 中选取需要的 JWK；context 是安全上下文（这里未使用）。
//        //jwkSelector.select(new JWKSet()) 的作用是：告诉 JWKSource 将当前持有的所有 JWK 以 JWKSet 的形式返回。因为 JWKSource 内部可能持有多个 JWK（例如用于密钥轮换），这个 Lambda 表达式就是“选择全部”的逻辑。
//        JWKSet jwkSet = jwkSource.get((jwkSelector, context) -> jwkSelector.select(new JWKSet()));
//        return jwkSet.toJSONObject();
//    }
}