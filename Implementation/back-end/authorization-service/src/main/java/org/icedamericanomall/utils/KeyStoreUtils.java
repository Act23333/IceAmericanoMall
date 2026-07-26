package org.icedamericanomall.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Slf4j
public class KeyStoreUtils {
    public static Object[] loadPrivateKeyAndCertificate(String keyStorePath,
                                                        String keyStorePassword,
                                                        String keyAlias,
                                                        String keyPassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new ClassPathResource(keyStorePath).getInputStream()) {
            keyStore.load(is, keyStorePassword.toCharArray());
        }
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(keyAlias);
        log.info("成功从密钥库加载私钥和证书，别名: {}", keyAlias);
        return new Object[]{privateKey, certificate};
    }
}