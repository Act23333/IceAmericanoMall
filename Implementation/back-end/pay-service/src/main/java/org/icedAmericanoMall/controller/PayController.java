package org.icedAmericanoMall.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.service.PayOrderService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.util.HashMap;
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

    /**
     * 微信支付回调通知。
     * 提取 HTTP 请求头中的微信签名参数 + 原始请求体，传递给 PaymentClient 验签。
     */
    @PostMapping("/callback/wechat")
    public Result<String> wechatCallback(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("Wechatpay-Signature", request.getHeader("Wechatpay-Signature"));
        params.put("Wechatpay-Nonce", request.getHeader("Wechatpay-Nonce"));
        params.put("Wechatpay-Timestamp", request.getHeader("Wechatpay-Timestamp"));
        params.put("Wechatpay-Serial", request.getHeader("Wechatpay-Serial"));

        // 读取原始请求体
        try {
            BufferedReader reader = request.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            params.put("body", body.toString());
        } catch (Exception e) {
            return Result.error(400, "无法读取回调请求体");
        }

        payOrderService.handleCallback(params);
        return Result.ok("success", "SUCCESS");
    }

    @GetMapping("/order/{orderNo}/status")
    public Result<PayOrderEntity> status(@PathVariable String orderNo) {
        PayOrderEntity payOrder = payOrderService.queryStatus(orderNo);
        return Result.ok(payOrder);
    }
}
