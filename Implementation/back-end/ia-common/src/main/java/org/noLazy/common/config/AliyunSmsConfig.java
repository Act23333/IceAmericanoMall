//package org.noLazy.common.config;
//
//import com.aliyuncs.DefaultAcsClient;
//import com.aliyuncs.IAcsClient;
//import com.aliyuncs.profile.DefaultProfile;
//import lombok.RequiredArgsConstructor;
//import org.noLazy.common.utils.AliyunSmsProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@RequiredArgsConstructor
//public class AliyunSmsConfig {
//
//    private final AliyunSmsProperties properties;
//
//    @Bean
//    public IAcsClient acsClient() {
//        DefaultProfile profile = DefaultProfile.getProfile(
//                properties.getRegionId(),
//                properties.getAccessKeyId(),
//                properties.getAccessKeySecret()
//        );
//        return new DefaultAcsClient(profile);
//    }
//}