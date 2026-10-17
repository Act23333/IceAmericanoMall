package org.icedamericanomall.logistics.adapter.internal;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.logistics.app.LogisticsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 物流内部端点（{@code PUT /internal/logistics/{orderId}/status}）的<b>不存在承载</b> —— 批次 ①。
 *
 * <p>这个端点是本批<b>内部面</b>的代表：{@code /internal/**} 不包 {@code Result}，业务异常返回
 * <b>真实 HTTP 状态码 + 裸 {@code ErrorInfo}</b>（{@code code/msg/category/retryable/traceId}，ADR-004）。
 *
 * <p><b>改前必失败</b>：{@code LOGISTICS_NOT_FOUND(3035)} 改造前配 200 —— 于是「包裹不存在」
 * 是一条 <b>HTTP 200</b> 响应，Feign 侧不会进错误解码器，调用方要么按 2xx 当成功、
 * 要么因响应体无法反序列化成声明返回类型（{@code void}）而拿到一个语义模糊的结果。
 * 现在 404 + {@code category=CLIENT}：调用方据此<b>原样透传</b>「物流信息不存在」，
 * <b>不得</b>改写成 {@code SYSTEM_BUSY}（{@code 09-constraints} §三·八·1）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalLogisticsController — 包裹不存在 404 + ErrorInfo(CLIENT)")
class InternalLogisticsControllerNotFoundTest {

    @Mock
    private LogisticsManager logisticsManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalLogisticsController(logisticsManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("更新不存在的包裹 → 404 + 3035 + category=CLIENT + retryable=false")
    void shouldReturnTyped404_whenPackageMissing() throws Exception {
        doThrow(new BizException(ErrorCode.LOGISTICS_NOT_FOUND, "物流信息不存在"))
                .when(logisticsManager).updateStatus(9L, 1, 3);

        mockMvc.perform(MockMvcRequestBuilders.put("/internal/logistics/9/status")
                        .param("status", "3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3035))
                .andExpect(jsonPath("$.msg").value("物流信息不存在"))
                .andExpect(jsonPath("$.category").value(ErrorCategory.CLIENT.name()))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    @DisplayName("非法状态码 → 仍 400 + 1101（参数错误，未被本批波及）")
    void shouldKeep400_whenStatusIllegal() throws Exception {
        doThrow(new BizException(ErrorCode.PARAM_ERROR, "非法物流状态: 99"))
                .when(logisticsManager).updateStatus(9L, 1, 99);

        mockMvc.perform(MockMvcRequestBuilders.put("/internal/logistics/9/status")
                        .param("status", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1101));
    }
}
