package org.icedamericanomall.trade.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.app.order.OrderCreationManager;
import org.icedamericanomall.trade.app.order.OrderLifecycleManager;
import org.icedamericanomall.trade.app.order.OrderQueryManager;
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
 * 订单详情（{@code GET /api/trade/order/{orderNo}}）的存在性承载 —— 批次 ① 状态码归位。
 *
 * <p><b>改前必失败</b>：{@code ORDER_NOT_FOUND(3009)} 改造前配 200，查一个不存在的订单返回
 * <b>HTTP 200 + body code 3009</b>（前端必须先解 body 才知道是「没有了」还是「查到了空数据」）；
 * 现返回 <b>404</b>，且业务码仍是 3009（前端据此区分「订单不存在」与「店铺/SKU 不存在」）。
 *
 * <p><b>同时守住批次 ③ 的归属分支</b>：非本人订单仍是 403 + 1013 —— 若本批把「无权」也改成 404，
 * 商家与买家都会把「越权」误读成「订单被删了」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController — 订单不存在 404/3009；无权查看仍 403/1013")
class OrderControllerNotFoundTest {

    @Mock
    private OrderCreationManager orderCreationManager;

    @Mock
    private OrderLifecycleManager orderLifecycleManager;

    @Mock
    private OrderQueryManager orderQueryManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderCreationManager, orderLifecycleManager, orderQueryManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("订单不存在（本人视角）→ 404 + 3009（不再是 200）")
    void shouldReturn404_whenOrderMissing() throws Exception {
        when(orderQueryManager.getOwnedOrderDetail(anyString(), any()))
                .thenThrow(new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/order/" + "0f1e2d3c4b5a69788796a5b4c3d2e1f0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3009))
                .andExpect(jsonPath("$.msg").value("订单不存在"));
    }

    @Test
    @DisplayName("订单归属他人 → 仍 403 + 1013（不得被 not-found 掩盖）")
    void shouldKeep403_whenOrderOwnedByAnother() throws Exception {
        when(orderQueryManager.getOwnedOrderDetail(anyString(), any()))
                .thenThrow(new org.icedamericanomall.common.exception.ForbiddenException(
                        ErrorCode.FORBIDDEN, "无权查看该订单"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/trade/order/" + "0f1e2d3c4b5a69788796a5b4c3d2e1f0"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1013));
    }
}
