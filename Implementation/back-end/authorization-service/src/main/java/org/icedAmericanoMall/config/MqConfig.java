//package org.icedAmericanoMall.config;
//
//
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
//import org.springframework.amqp.support.converter.MessageConverter;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// * @ClassName: MqConfig
// * @Description: TODO
// * @Author: noLazy
// * @Date: 2025/9/2 5:10
// * @Version: 1.0.0
// * @ProjectName: hmall
// * @Package: com.hmall.common.config
// */
//@Configuration
//@ConditionalOnClass(value = RabbitTemplate.class, name = "org.springframework.amqp.rabbit.annotation.RabbitListener")
//public class MqConfig {
//    @Bean
//    public MessageConverter messageConverter() {
////        new Jackson2JsonMessageConverter().setCreateMessageIds(true);
//        return new Jackson2JsonMessageConverter();
//    }
//}
