package org.icedamericanomall.item.adapter.web;

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
 * P38 短码解析端点适配层接线测试 —— {@code GET /api/item/spu/by-code/{code}}（契约 §4.4.1）。
 *
 * <p>锁定三件事：
 * <ol>
 *   <li><b>路由不被 {@code /{spuId}} 吞掉</b>：{@code by-code} 是字面段，Spring 字面段优先；</li>
 *   <li><b>形态非法 → 400</b>（拼错的物料码应得「参数不对」，不是「商品不存在」）；
 *       且非法形态<b>绝不触达应用层</b>（否则会被解释成 404）；</li>
 *   <li><b>短码不存在 → 404 + 1103</b>（真状态码，落地页才能落到宿主错误页）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuController — P38 短码详情端点")
class SpuControllerByCodeTest {

    /** 合法短码（10 位小写 hex）。 */
    private static final String CODE = "8f3a1c9d20";

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
                .build();
    }

    @Test
    @DisplayName("P38 — 合法短码 → 200 + Result 信封 + SpuVO（路由未被子资源段吞掉）")
    void shouldReturnDetail_whenCodeValid() throws Exception {
        SpuVO vo = new SpuVO();
        vo.setSpuId("0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        vo.setName("手工陶瓷茶杯");
        when(spuManager.getSpuDetailByCode(CODE)).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/by-code/" + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("手工陶瓷茶杯"));
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
