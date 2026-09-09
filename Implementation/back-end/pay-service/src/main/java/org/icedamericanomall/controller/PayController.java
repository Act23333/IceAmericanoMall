package org.icedamericanomall.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.vo.PayOrderVO;
import org.icedamericanomall.enums.PayChannelEnum;
import org.icedamericanomall.manager.PayManager;
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

    private final PayManager payManager;

    /**
     * 发起支付。channel 可选 WECHAT（默认）/ALIPAY/BALANCE。
     * 余额支付即时完成（无二维码）；微信/支付宝返回支付链接。
     */
    @PostMapping("/order/{orderNo}")
    public Result<PayOrderVO> initiate(@PathVariable String orderNo,
                                       @RequestParam(defaultValue = "WECHAT") String channel) {
        Long userId = UserContext.getUserId();
        return Result.ok(payManager.initiatePayment(orderNo, userId, PayChannelEnum.of(channel)));
    }

    /**
     * 微信支付回调通知。
     * 提取 HTTP 请求头中的微信签名参数 + 原始请求体，传递给 PayManager 验签处理。
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

        payManager.handleCallback(params, PayChannelEnum.WECHAT);
        return Result.ok("success", "SUCCESS");
    }

    /**
     * 支付宝支付回调（表单参数，含 out_trade_no）。Mock 环境验签恒通过。
     */
    @PostMapping("/callback/alipay")
    public String alipayCallback(@RequestParam Map<String, String> params) {
        payManager.handleCallback(params, PayChannelEnum.ALIPAY);
        return "success";
    }

    @GetMapping("/order/{orderNo}/status")
    public Result<PayOrderVO> status(@PathVariable String orderNo) {
        return Result.ok(payManager.queryStatus(orderNo));
    }
}
