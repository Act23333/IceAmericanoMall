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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SpuController P37 端点适配层接线测试 —— GET /api/item/product/{id}/wxa-code
 * 公开匿名委托 {@link SpuShareManager}；1103 经全局异常处理器落 404 + code 1103。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuController — P37 小程序码端点委托")
class SpuControllerWxaCodeTest {

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
    @DisplayName("P37 wxa-code — Manager 返回 VO → Result 包装 code=200 + data.url")
    void shouldWrapResult_whenWxaCodeGenerated() throws Exception {
        when(spuShareManager.getWxaCode(7L))
                .thenReturn(WxaCodeVO.of("http://presigned/wxa-code/product/7.png"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/product/7/wxa-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("http://presigned/wxa-code/product/7.png"));
    }

    @Test
    @DisplayName("P37 wxa-code — 商品不存在 1103 → 经全局异常处理器落 404")
    void shouldReturn404_whenSpuNotFound() throws Exception {
        when(spuShareManager.getWxaCode(404L))
                .thenThrow(new BizException(ErrorCode.NO_RESOURCE_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/product/404/wxa-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1103));
    }
}
