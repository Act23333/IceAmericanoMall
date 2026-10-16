package org.icedamericanomall.item.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
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
 * SpuController P37 端点适配层接线测试 —— GET /api/item/spu/{spuId}/wxa-code
 * 公开匿名委托 {@link SpuShareManager}；1103 经全局异常处理器落 404 + code 1103；
 * 路径变量为 <b>32-hex SPU 业务键</b>（ADR-062 §决策 2/3），形态非法即 400 fail-fast。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuController — P37 小程序码端点委托")
class SpuControllerWxaCodeTest {

    /** 合法 SPU 业务键（32 位小写 hex 形态，非技术键）。 */
    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 另一个合法业务键（用于 404 分支，避免与上例共用 mock 期望）。 */
    private static final String MISSING_SPU_ID = "11223344556677889900112233445566";

    @Mock
    private SpuManager spuManager;

    @Mock
    private KgPairingManager kgPairingManager;

    @Mock
    private SpuShareManager spuShareManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 注册全局异常处理器：standalone 环境不会自动装配 ia-common 的 @RestControllerAdvice
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SpuController(spuManager, kgPairingManager, spuShareManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("P37 wxa-code — 业务键入参 → Manager 收到归一化业务键，Result 包装 code=200 + data.url")
    void shouldWrapResult_whenWxaCodeGenerated() throws Exception {
        when(spuShareManager.getWxaCode(SPU_ID))
                .thenReturn(WxaCodeVO.of("http://presigned/wxa-code/product/7.png"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/" + SPU_ID + "/wxa-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("http://presigned/wxa-code/product/7.png"));
    }

    @Test
    @DisplayName("P37 wxa-code — 商品不存在 1103 → 经全局异常处理器落 404")
    void shouldReturn404_whenSpuNotFound() throws Exception {
        when(spuShareManager.getWxaCode(MISSING_SPU_ID))
                .thenThrow(new BizException(ErrorCode.NO_RESOURCE_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/" + MISSING_SPU_ID + "/wxa-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103));
    }

    @Test
    @DisplayName("P37 wxa-code — 非 32-hex 入参（如技术键 7）→ 适配层 400 PARAM_ERROR fail-fast，不落「查不到」")
    void shouldReturn400_whenSpuIdMalformed() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/spu/7/wxa-code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1101));

        // 关键：非法形态绝不落到应用层（否则会被解释成「商品不存在」→ 404）
        verify(spuShareManager, never()).getWxaCode(anyString());
    }
}
