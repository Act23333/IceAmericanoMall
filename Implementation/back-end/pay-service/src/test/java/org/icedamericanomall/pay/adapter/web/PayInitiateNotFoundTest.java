package org.icedamericanomall.pay.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.pay.app.PayManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 发起支付（{@code POST /api/pay/order/{orderNo}}）的存在性承载 —— 批次 ① 状态码归位。
 *
 * <p><b>改前必失败</b>：{@code ORDER_NOT_FOUND(3009)} 改造前配 200 —— 对一个不存在的订单发起支付
 * 返回 <b>HTTP 200 + body code 3009</b>，前端「下单后自动拉起支付」的链路会把它当成成功去拿
 * 支付链接（拿到 null 才报错）；现返回 <b>404</b>，前端可直接走「订单已失效」分支。
 *
 * <p><b>本类刻意只测这一个端点</b>：{@code /callback/wechat} 是渠道协议端点（异常一律被控制器
 * 兜成 500 FAIL 触发重推，见 {@code PayControllerTest}），不参与本批的 404 口径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayController — 发起支付时订单不存在 404/3009")
class PayInitiateNotFoundTest {

    private static final String ORDER_NO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    @Mock
    private PayManager payManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PayController(payManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("发起支付 — 订单不存在 → 404 + 3009（不再是 200）")
    void shouldReturn404_whenOrderMissingOnInitiate() throws Exception {
        when(payManager.initiatePayment(anyString(), any(), any()))
                .thenThrow(new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + ORDER_NO));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/pay/order/" + ORDER_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3009));
    }
}
