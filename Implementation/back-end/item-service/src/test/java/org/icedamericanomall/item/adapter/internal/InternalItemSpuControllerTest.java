package org.icedamericanomall.item.adapter.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuDetailCO;
import org.icedamericanomall.item.app.SpuManager;
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
 * I70 商品详情内部端点（{@code GET /internal/item/spu/{spuId}}，契约 §4.14.5.2）接线守卫。
 *
 * <p>锁定四条<b>承载层纪律</b>（与公开 P2 的差别只有这一层，其余必须同源）：
 * <ol>
 *   <li><b>不包 {@code Result} 信封</b>（ADR-004）：成功响应体就是 {@link SpuDetailCO} 本身，
 *       顶层是 {@code spu_id}/{@code spu_name} 等字段，<b>没有</b> {@code code}/{@code data}——
 *       Feign 侧返回类型也随之是裸 CO；</li>
 *   <li><b>形态非法 → 400 + 1101</b>，且<b>不触达应用层</b>（拼错的业务键必须是「参数不对」，
 *       不能静默变成「查不到」）；</li>
 *   <li><b>不存在/已删除 → 404 + 1103</b>（真 HTTP 状态 + 极简 {@code {"code","msg"}}，
 *       不是 200 信封）；</li>
 *   <li><b>与 P2 同源</b>：控制器只调 {@link SpuManager#getSpuDetailCO(String)}，
 *       不自己装配（第二套装配必然漂移）。</li>
 * </ol>
 *
 * <p><b>命名策略对齐生产</b>：{@code standaloneSetup} 默认用 camelCase 的 {@code ObjectMapper}，
 * 与各服务 {@code application.yml} 的 {@code SNAKE_CASE} 不一致 —— 不显式装的话，
 * 这里断言的 `spu_name` 线上键根本读不到（本批的整个改名正是只有线格式级断言才守得住）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalItemSpuController — I70 内部详情端点（裸 CO / 400·1101 / 404·1103）")
class InternalItemSpuControllerTest {

    /** 合法 SPU 业务键（32-hex）。 */
    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 与生产一致：线上键 snake_case。 */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private SpuManager spuManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalItemSpuController(spuManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(WIRE_MAPPER))
                .build();
    }

    @Test
    @DisplayName("合法业务键 → 200 + 裸 SpuDetailCO（顶层无 code/data 信封）")
    void shouldReturnRawCO_whenSpuIdValid() throws Exception {
        when(spuManager.getSpuDetailCO(SPU_ID)).thenReturn(new SpuDetailCO(
                1L, SPU_ID, "手工陶瓷茶杯", "纯手工", "冰美精选", 1L,
                "https://cdn/x.jpg", null, null, 12, 1));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/item/spu/" + SPU_ID))
                .andExpect(status().isOk())
                // 裸 CO：字段直接在顶层（内部面不包 Result，ADR-004 / 契约 I70 响应列）
                .andExpect(jsonPath("$.spu_id").value(SPU_ID))
                // 名称键为 spu_name（契约 §1.8④），且不得残留旧键 name
                .andExpect(jsonPath("$.spu_name").value("手工陶瓷茶杯"))
                .andExpect(jsonPath("$.name").doesNotExist())
                // 信封字段必须不存在 —— 一旦包了 Result，Feign 侧裸 CO 解码会静默落 null
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("业务键形态非法（过短 / 超长 / 非 hex）→ 400 + 1101，且不触达应用层")
    void shouldReturn400_whenSpuIdMalformed() throws Exception {
        // 注意：大写 hex 与 36 位连字符 UUID 是**合法**形态（IdGenerator.requireWire 会归一化），
        // 故不在此列 —— 拿它们当非法样例会把「归一化能力」误判成缺陷。
        for (String malformed : new String[]{"7", "0f1e2d3c4b5a69788796a5b4c3d2e1f00", "not-a-hex"}) {
            mockMvc.perform(MockMvcRequestBuilders.get("/internal/item/spu/" + malformed))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(1101));
        }
        verify(spuManager, never()).getSpuDetailCO(anyString());
    }

    @Test
    @DisplayName("商品不存在/已删除 → 404 + 1103（真 HTTP 状态 + 极简 {code,msg}）")
    void shouldReturn404_whenSpuMissing() throws Exception {
        when(spuManager.getSpuDetailCO(SPU_ID))
                .thenThrow(new BizException(ErrorCode.NO_RESOURCE_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/internal/item/spu/" + SPU_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103))
                .andExpect(jsonPath("$.msg").exists());
    }
}
