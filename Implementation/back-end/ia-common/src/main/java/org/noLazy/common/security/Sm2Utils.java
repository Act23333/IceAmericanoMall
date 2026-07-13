package org.noLazy.common.security;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SM2 国产密码工具 —— 非对称加密/解密（V2.5，hutool 5.8.43 API）。
 * 仅在 {@code sm2.enabled=true} 且配置公私钥时激活；默认关闭。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sm2.enabled", havingValue = "true")
public class Sm2Utils {

    private final SM2 sm2;

    public Sm2Utils(Sm2Properties props) {
        this.sm2 = props.getPrivateKey() != null && props.getPublicKey() != null
                ? SmUtil.sm2(props.getPrivateKey(), props.getPublicKey())
                : SmUtil.sm2();
        log.info("SM2 初始化完成（enabled=true, hutool 5.8.x）");
    }

    /** 用公钥加密后 Base64 输出。 */
    public String encryptBase64(String plaintext) {
        return sm2.encryptBase64(plaintext, cn.hutool.crypto.asymmetric.KeyType.PublicKey);
    }

    /** 用私钥解密。 */
    public String decryptStr(String ciphertext) {
        return sm2.decryptStr(ciphertext, cn.hutool.crypto.asymmetric.KeyType.PrivateKey);
    }
}
