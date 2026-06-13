package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.service.PayOrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayOrderService payOrderService;

    @PostMapping("/order/{orderNo}")
    public Result<PayOrderEntity> initiate(@PathVariable String orderNo) {
        Long userId = UserContext.getUser();
        PayOrderEntity payOrder = payOrderService.initiatePayment(orderNo, userId);
        return Result.ok(payOrder);
    }

    @PostMapping("/callback/wechat")
    public Result<String> wechatCallback(@RequestBody Map<String, String> params) {
        payOrderService.handleCallback(params);
        return Result.ok("success", "SUCCESS");
    }

    @GetMapping("/order/{orderNo}/status")
    public Result<PayOrderEntity> status(@PathVariable String orderNo) {
        PayOrderEntity payOrder = payOrderService.queryStatus(orderNo);
        return Result.ok(payOrder);
    }
}
