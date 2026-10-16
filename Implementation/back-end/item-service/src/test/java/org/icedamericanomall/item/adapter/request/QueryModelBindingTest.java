package org.icedamericanomall.item.adapter.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.icedamericanomall.common.web.JacksonQueryModelArgumentResolver;
import org.icedamericanomall.common.web.QueryModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link QueryModel} 端到端绑定验证（ADR-056 第 ③ 类载体）。
 *
 * <p>生产环境中该解析器由 {@code ia-common} 的 {@code QueryModelWebConfig} 注册、命名策略来自
 * {@code application.yml} 的 {@code spring.jackson.property-naming-strategy: SNAKE_CASE}；
 * 因 {@code standaloneSetup} 不会自动装载 {@code WebMvcConfigurer}，此处**手工注册同一解析器**
 * 并用**同一命名策略**构造 {@code ObjectMapper}，以复现真实链路的语义。
 *
 * <p>锁死两点：① 契约的蛇形名能绑进 POJO 的驼峰字段（此前静默为 null）；
 * ② camelCase 写法**不被接受**（不做双写法迎合，命名规范只有一份）。
 */
@DisplayName("QueryModel — 蛇形查询参数经 Jackson 绑定到 POJO（控制器保持 POJO 形参）")
class QueryModelBindingTest {

    /** 探针控制器：形参与生产一致 —— 单个 POJO，不摊开参数列表。 */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/page")
        String page(@QueryModel SpuPageReq req) {
            return req.getCategoryId() + "|" + req.getSellerId() + "|" + req.getPage();
        }
    }

    /** 与生产同策略的 ObjectMapper（生产由 application.yml 提供）。 */
    private static final ObjectMapper SNAKE_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // 与生产一致：Spring Boot 默认关闭该开关（@RequestBody 同样忽略未知字段）
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setCustomArgumentResolvers(new JacksonQueryModelArgumentResolver(SNAKE_MAPPER))
            .build();

    @Test
    @DisplayName("P1 — ?category_id=&seller_id= 绑定到 SpuPageReq 的 categoryId/sellerId")
    void shouldBindSnakeQueryParams_whenQueryModel() throws Exception {
        mockMvc.perform(get("/probe/page?category_id=5&seller_id=9"))
                .andExpect(status().isOk())
                .andExpect(content().string("5|9|1"));
    }

    @Test
    @DisplayName("缺省值沿用 DTO 初始化（page 默认 1），且未传字段为 null 不报错")
    void shouldKeepFieldDefault_whenParamAbsent() throws Exception {
        mockMvc.perform(get("/probe/page"))
                .andExpect(status().isOk())
                .andExpect(content().string("null|null|1"));
    }

    @Test
    @DisplayName("camelCase 不被接受：视为未知字段而落空（与 @RequestBody 同语义，不迎合双写法）")
    void shouldNotBindCamel_whenSnakeIsTheStandard() throws Exception {
        mockMvc.perform(get("/probe/page?categoryId=5&sellerId=9"))
                .andExpect(status().isOk())
                .andExpect(content().string("null|null|1"));
    }
}
