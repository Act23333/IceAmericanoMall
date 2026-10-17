package org.icedamericanomall.item.adapter.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.app.SpuManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I14 库存扣减内部端点（{@code POST /internal/item/sku/deduct}）的<b>语义拆分</b>守卫
 * （批次 ③「拆双义错误码」）。
 *
 * <p><b>锁定两条互斥结论</b>（同一端点、只差入参形态）：
 * <ol>
 *   <li><b>业务键形态非法</b> → <b>HTTP 400</b> + {@code code = 1101}（{@code PARAM_ERROR}），
 *       且<b>不触达应用层</b>。拼错的键是调用方的参数错误，绝不能变成「商品已下架」；</li>
 *   <li><b>形态合法但 SKU 不存在</b> → {@code code = 3016}（{@code SKU_NOT_FOUND}），
 *       <b>不是</b> 1101。</li>
 * </ol>
 *
 * <p><b>为何第 2 条不断言 HTTP 状态码</b>：{@code 3016} 的 {@code HttpStatus} 本批<b>刻意不动</b>
 * （仍是 200，按用户裁决的顺序「先拆语义、再改状态码」）；把它钉成 200 会让批次 ①（归位 404）
 * 无谓地红。本批要钉的是<b>码与语义的一一对应</b>，状态码归位归 ①。
 *
 * <p><b>内部面承载</b>：{@code /internal/**} 不包 {@code Result}，错误体是
 * {@code ErrorInfo}（{@code code/msg/category/retryable/traceId}，ADR-004 + 09 §三·八·3），
 * 故此处顺带断言 {@code category} 取自派生（{@code CLIENT}）而非手填。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalSkuController — I14 形态非法 400/1101 vs SKU 不存在 3016")
class InternalSkuControllerTest {

    /** 合法 SKU 业务键（32-hex）。 */
    private static final String SKU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 形态合法但库里不存在。 */
    private static final String SKU_ID_ABSENT = "deadbeefdeadbeefdeadbeefdeadbeef";

    /** 与生产一致：线上键 snake_case。 */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Mock
    private SkuManager skuManager;

    @Mock
    private SpuManager spuManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalSkuController(skuManager, spuManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(WIRE_MAPPER))
                .build();
    }

    private static String deductBody(String skuId) {
        return "[{\"sku_id\":\"" + skuId + "\",\"quantity\":1}]";
    }

    @Test
    @DisplayName("形态非法（拼错/长度错/非 hex）→ 400 + 1101 + category=CLIENT，且不触达应用层")
    void shouldReturn400_whenSkuIdMalformed() throws Exception {
        for (String malformed : new String[]{"prod-001", "1122334455", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"}) {
            mockMvc.perform(MockMvcRequestBuilders.post("/internal/item/sku/deduct")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deductBody(malformed)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                    .andExpect(jsonPath("$.category").value(ErrorCategory.CLIENT.name()))
                    .andExpect(jsonPath("$.retryable").value(false));
        }
        verify(skuManager, never()).deductStock(anyString(), anyInt());
    }

    @Test
    @DisplayName("形态合法但 SKU 不存在 → 3016（不是 1101），HTTP 状态码本批不动（归位由批次 ①）")
    void shouldReturnSkuNotFound_whenSkuAbsent() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ErrorCode.SKU_NOT_FOUND))
                .when(skuManager).deductStock(SKU_ID_ABSENT, 1);

        mockMvc.perform(MockMvcRequestBuilders.post("/internal/item/sku/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deductBody(SKU_ID_ABSENT)))
                .andExpect(jsonPath("$.code").value(ErrorCode.SKU_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(
                        ErrorCode.PARAM_ERROR.getCode())));
    }

    @Test
    @DisplayName("合法业务键 → 200 且无 body（内部裸 void，不包信封）")
    void shouldReturnOk_whenSkuIdValid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/item/sku/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deductBody(SKU_ID)))
                .andExpect(status().isOk());

        verify(skuManager).deductStock(SKU_ID, 1);
    }
}
