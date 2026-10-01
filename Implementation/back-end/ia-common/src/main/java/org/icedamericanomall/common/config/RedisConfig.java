package org.icedamericanomall.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * RedisTemplate 序列化配置（String Key + JSON Value）。
 *
 * <p>{@code @ConditionalOnClass} 守卫（V5.2 补齐，R-1 遗留项）：
 * ia-common 对 {@code spring-boot-starter-data-redis} 的依赖是 <b>optional</b>，
 * 未引入 Redis 的服务（如 notification-service）类路径上没有
 * {@code RedisTemplate} / {@code RedisConnectionFactory}。此前本类无条件注册，
 * 会导致这类服务启动即 {@code NoClassDefFoundError}。
 * 守卫后：类路径无 Redis 时本配置整体跳过，服务正常启动。
 *
 * <p>{@code @Primary} 保留：本类刻意覆盖 Spring Boot 自动配置的
 * {@code RedisTemplate<Object,Object>}（后者用 JDK 序列化），
 * 把 JSON 序列化的模板标为优先，属于有意的实现选择而非 Mock 遮蔽。
 */
@Configuration
@ConditionalOnClass({RedisConnectionFactory.class, RedisTemplate.class})
public class RedisConfig {
    @Primary
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 1. Key 使用 String 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 2. Value 使用 Jackson2JsonRedisSerializer
        // 构建 ObjectMapper（不再使用 LazyCollectionOption）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 可选：启用多态类型支持（存储对象时记录类型信息）
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        // 通过构造函数传入 ObjectMapper（取代已废弃的 setObjectMapper）
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }
}