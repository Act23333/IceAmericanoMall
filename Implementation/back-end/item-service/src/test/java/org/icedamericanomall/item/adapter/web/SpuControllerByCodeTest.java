package org.icedamericanomall.item.adapter.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.app.SpuManager;
import org.icedamericanomall.item.app.kg.KgPairingManager;
import org.icedamericanomall.item.app.share.SpuShareManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品详情适配层接线测试 —— P38 {@code GET /api/item/spu/by-code/{code}} 与
 * P2 {@code GET /api/item/spu/{spuId}}（契约 §4.4.1）。
 *
 * <p>锁定四件事：
 * <ol>
 *   <li><b>路由不被 {@code /{spuId}} 吞掉</b>：{@code by-code} 是字面段，Spring 字面段优先；</li>
 *   <li><b>形态非法 → 400</b>（拼错的物料码应得「参数不对」，不是「商品不存在」）；
 *       且非法形态<b>绝不触达应用层</b>（否则会被解释成 404）；</li>
 *   <li><b>短码不存在 → 404 + 1103</b>（真状态码，落地页才能落到宿主错误页）；</li>
 *   <li><b>名称字段的线上键是 {@code spu_name}</b>，且 P2 与 P38 <b>同形</b>（契约 §1.8④）——
 *       该断言必须是<b>线格式级</b>（{@code SNAKE_CASE} 渲染后的 JSON 键），
 *       而不是 Java 字段名：同名字段改错命名策略时前者才会红。</li>
 * </ol>
 *
 * <p><b>为何在此显式装 {@code SNAKE_CASE} 的消息转换器</b>：{@code standaloneSetup} 默认用
 * camelCase 的 {@code ObjectMapper}，与生产（各服务 {@code application.yml} 声明
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE}）不一致；不装的话这里断言的是
 * 「Java 字段名」而非「线上键名」，2026-10-18「甲b」这种**键名收口**正是只有后者才能守住。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuController — P38 / P2 商品详情端点（含 spu_name 线格式断言）")
class SpuControllerByCodeTest {

    /** 合法短码（10 位小写 hex）。 */
    private static final String CODE = "8f3a1c9d20";

    /** 合法 SPU 业务键（32-hex）。 */
    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 与生产一致：线上键 snake_case（{@code spring.jackson.property-naming-strategy}）。 */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private SpuManager spuManager;

    @Mock
    private KgPairingManager kgPairingManager;

    @Mock
    private SpuShareManager spuShareManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SpuController(spuManager, kgPairingManager, spuShareManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(WIRE_MAPPER))
                .build();
    }

    @Test
    @DisplayName("P38 — 合法短码 → 200 + Result 信封 + SpuVO（路由未被子资源段吞掉）")
    void shouldReturnDetail_whenCodeValid() throws Exception {
        SpuVO vo = new SpuVO();
        vo.setSpuId(SPU_ID);
        vo.setSpuName("手工陶瓷茶杯");
        when(spuManager.getSpuDetailByCode(CODE)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/by-code/" + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 线格式：名称键必须是 spu_name（契约 §1.8④）
                .andExpect(jsonPath("$.data.spu_name").value("手工陶瓷茶杯"))
                // 同名旧键不得残留（否则前端按 name 读会「有时有值有时 undefined」）
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("P2 — 详情响应同样只出 spu_name（与 P38 同形，契约 §1.8④）")
    void shouldExposeSpuNameOnly_whenP2Detail() throws Exception {
        SpuVO vo = new SpuVO();
        vo.setSpuId(SPU_ID);
        vo.setSpuName("手工陶瓷茶杯");
        when(spuManager.getSpuDetail(SPU_ID)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/" + SPU_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spu_id").value(SPU_ID))
                .andExpect(jsonPath("$.data.spu_name").value("手工陶瓷茶杯"))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("P38 — 短码不存在 → 404 + 1103（真 HTTP 状态码，不是 200 信封）")
    void shouldReturn404_whenCodeUnknown() throws Exception {
        when(spuManager.getSpuDetailByCode(CODE))
                .thenThrow(new BizException(ErrorCode.NO_RESOURCE_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/by-code/" + CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103));
    }

    @Test
    @DisplayName("P38 — 短码形态非法（技术键 / 大写 / 超长）→ 400 + 1101，且不触达应用层")
    void shouldReturn400_whenCodeMalformed() throws Exception {
        for (String malformed : new String[]{"7", "8F3A1C9D20", "8f3a1c9d201", "not-a-code"}) {
            mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/by-code/" + malformed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1101));
        }
        verify(spuManager, never()).getSpuDetailByCode(anyString());
    }
}
