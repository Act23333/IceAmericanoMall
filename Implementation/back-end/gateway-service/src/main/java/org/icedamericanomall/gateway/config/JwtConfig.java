//package org.icedamericanomall.gateway.config;
//
//import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.oauth2.core.OAuth2TokenValidator;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
//import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
//import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
//import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
//
//import java.security.interfaces.RSAPublicKey;
//import java.util.List;
//
//@Configuration
//public class JwtConfig {
//
//    @Value("${jwt.public-key}")
//    private RSAPublicKey publicKey;
//
//    @Bean
//    public ReactiveJwtDecoder reactiveJwtDecoder(ReactiveStringRedisTemplate redisTemplate) {
//        // 1. 使用公钥创建 NimbusReactiveJwtDecoder
//        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
//
//        // 2. 构建一个委托验证器，将默认验证器与自定义验证器组合
//        OAuth2TokenValidator<Jwt> defaultValidator = new JwtTimestampValidator(); // 校验过期时间
//        OAuth2TokenValidator<Jwt> blacklistValidator = new JwtBlacklistValidator(redisTemplate);
//        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
//                List.of(defaultValidator, blacklistValidator)
//        );
//
//        // 3. 将组合后的验证器设置到 Decoder 中
//        jwtDecoder.setJwtValidator(validator);
//
//        return jwtDecoder;
//    }
//}