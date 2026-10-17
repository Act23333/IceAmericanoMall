package org.icedamericanomall.cart.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.icedamericanomall.cart.app.CartManager;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购物车适配层「<b>形态非法 → 400 / 1101</b>」守卫（批次 ③「拆双义错误码」）。
 *
 * <p><b>存在的理由</b>：{@code SKU_NOT_FOUND(3016)} 此前同时承载「SKU 不存在」与「业务键拼错」。
 * 拆分的<b>落点必须可观测</b> —— 只在应用层改断言，证明不了「拼错的键不会变成 3016」，
 * 因为生产路径上先经过的是 Controller。本类因此锁定三条：
 * <ol>
 *   <li>C2（{@code POST /api/cart/item}）、C3（{@code PUT}）请求体 {@code sku_id}
 *       形态非法 → <b>HTTP 400</b> + body {@code code = 1101}（{@code PARAM_ERROR}）；</li>
 *   <li>C4（{@code DELETE /api/cart/item/{skuId}}）、C5（{@code PATCH .../selected}）
 *       路径变量形态非法 → 同上；</li>
 *   <li>上述四种情况<b>都不触达应用层</b>（{@code CartManager} 零调用），
 *       且<b>不出 404</b>：拼错的键既不是「不存在」也不是「404」，是「请求写错了」。</li>
 * </ol>
 *
 * <p><b>为何断言 body 是 {@code Result} 信封</b>：{@code /api/**} 的信封结构本批<b>一字不变</b>
 * （{@code code}/{@code msg}/{@code data}）—— 拆语义不得顺手改信封（09 §三·七·1）。
 *
 * <p><b>命名策略对齐生产</b>：各服务 {@code application.yml} 用 {@code SNAKE_CASE} 线格式，
 * 故 {@code standaloneSetup} 显式装同款 {@code ObjectMapper}；不装的话
 * {@code sku_id} 在测试里根本读不到（那会把「线上键写错」误判成通过）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartController — 业务键形态非法必须 400/1101（不出 404、不进应用层）")
class CartControllerTest {

    /** 与生产一致：线上键 snake_case。 */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private CartManager cartManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(WIRE_MAPPER))
                .build();
    }

    /** 断言「HTTP 400 + body code 1101 + Result 信封字段在位」（并因此隐式排除 404 与 3016）。 */
    private void assertParamError(ResultActions actions) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.msg").exists());
    }

    @Test
    @DisplayName("C2 加购 — 请求体 sku_id 形态非法 → 400 + 1101（不是 3016、不是 404），不进应用层")
    void shouldReturn400_whenAddBodySkuIdMalformed() throws Exception {
        for (String malformed : new String[]{"prod-001", "1122334455", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"}) {
            assertParamError(mockMvc.perform(MockMvcRequestBuilders.post("/api/cart/item")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku_id\":\"" + malformed + "\",\"quantity\":1}")));
        }
        verify(cartManager, never()).addItem(any(), any());
    }

    @Test
    @DisplayName("C3 改数量 — 请求体 sku_id 形态非法 → 400 + 1101，不进应用层")
    void shouldReturn400_whenUpdateBodySkuIdMalformed() throws Exception {
        assertParamError(mockMvc.perform(MockMvcRequestBuilders.put("/api/cart/item")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku_id\":\"prod-001\",\"quantity\":2}")));
        verify(cartManager, never()).updateItem(any(), any());
    }

    @Test
    @DisplayName("C4 删除 — 路径变量 skuId 形态非法 → 400 + 1101，不进应用层")
    void shouldReturn400_whenRemovePathVariableMalformed() throws Exception {
        assertParamError(mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/cart/item/prod-001")));
        verify(cartManager, never()).removeItem(any(), any());
    }

    @Test
    @DisplayName("C5 改选中 — 路径变量 skuId 形态非法 → 400 + 1101，不进应用层")
    void shouldReturn400_whenSelectPathVariableMalformed() throws Exception {
        assertParamError(mockMvc.perform(MockMvcRequestBuilders
                .patch("/api/cart/item/1122334455/selected").param("selected", "true")));
        verify(cartManager, never()).selectItem(any(), any(), any());
    }
}
