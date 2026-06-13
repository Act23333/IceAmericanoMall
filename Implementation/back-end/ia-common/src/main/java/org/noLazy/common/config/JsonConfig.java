package org.noLazy.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class JsonConfig {

    /**
     * 全局生效：所有使用该 ObjectMapper 的地方（如 @RestController 返回、RestTemplate 等）都会将 Long 和 BigInteger 转为字符串。
     * 可能副作用：
     * 如果前端期望数字类型（例如用于数值计算），需要前后端协商统一。
     * 某些 API 文档（如 Swagger）可能显示类型为 string，需手动标注。
     * @return
     * 使用 Jackson2ObjectMapperBuilderCustomizer 做全局配置（如长整型转字符串、日期格式化），这样所有 JSON 处理都会统一。
     * 如果只需要为特定的 API 或转换器定制，可以单独创建 Jackson2JsonMessageConverter 并注入一个独立的 ObjectMapper，而不影响全局
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return jacksonObjectMapperBuilder -> {
            // long -> string
            jacksonObjectMapperBuilder.serializerByType(Long.class, ToStringSerializer.instance);
            jacksonObjectMapperBuilder.serializerByType(BigInteger.class, ToStringSerializer.instance);
        };
    }
}