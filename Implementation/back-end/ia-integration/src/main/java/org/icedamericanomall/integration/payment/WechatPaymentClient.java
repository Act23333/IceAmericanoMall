package org.icedamericanomall.integration.payment;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.payment.WechatPayProperties;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付 API v3 Native 支付 — {@code wechat.pay.merchant-id} 配置后激活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(NativePayService.class)
@ConditionalOnProperty(name = "wechat.pay.merchant-id")
public class WechatPaymentClient implements PaymentClient {

    private final WechatPayProperties props;
    private NativePayService nativePayService;
    private RSAAutoCertificateConfig rsaConfig;

    @PostConstruct
    public void init() {
        this.rsaConfig = new RSAAutoCertificateConfig.Builder()
                .merchantId(props.getMerchantId())
                .privateKeyFromPath(props.getPrivateKeyPath())
                .merchantSerialNumber(props.getMerchantSerialNumber())
                .apiV3Key(props.getApiV3Key())
                .build();
        this.nativePayService = new NativePayService.Builder().config(rsaConfig).build();
        log.info("微信支付 SDK 初始化完成: merchantId={}", props.getMerchantId());
    }

    @Override
    public String initiatePayment(String orderNo, int amount, String description) {
        PrepayRequest request = new PrepayRequest();
        request.setMchid(props.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(orderNo);
        request.setNotifyUrl(props.getNotifyUrl());
        Amount amountObj = new Amount();
        amountObj.setTotal(amount);
        request.setAmount(amountObj);
        try {
            var response = nativePayService.prepay(request);
            log.info("微信支付 Native 下单: orderNo={}", orderNo);
            return response.getCodeUrl();
        } catch (Exception e) {
            log.error("微信支付下单失败: orderNo={}", orderNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "支付下单失败: " + e.getMessage());
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        try {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(params.get("Wechatpay-Serial"))
                    .nonce(params.get("Wechatpay-Nonce"))
                    .signature(params.get("Wechatpay-Signature"))
                    .timestamp(params.get("Wechatpay-Timestamp"))
                    .body(params.get("body"))
                    .build();
            new NotificationParser(rsaConfig).parse(requestParam, Object.class);
            return true;
        } catch (Exception e) {
            log.error("微信支付回调验签失败", e);
            return false;
        }
    }

    @Override
    public String queryStatus(String payOrderNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(payOrderNo);
        request.setMchid(props.getMerchantId());
        try {
            return nativePayService.queryOrderByOutTradeNo(request).getTradeState().name();
        } catch (Exception e) {
            log.error("微信支付状态查询失败: payOrderNo={}", payOrderNo, e);
            return null;
        }
    }

    /**
     * Mock 微信支付 — 未配置 merchant-id 时默认激活。
     */
    @Slf4j
    @Component
    @ConditionalOnProperty(name = "wechat.pay.merchant-id", havingValue = "false", matchIfMissing = true)
    public static class MockWechatPaymentClient implements PaymentClient {

        @Override
        public String initiatePayment(String orderNo, int amount, String description) {
            log.info("===== MOCK WECHAT PAY ===== OrderNo: {}, Amount: {} 分", orderNo, amount);
            return "https://mock-pay.example.com/qrcode?order=" + orderNo;
        }

        @Override
        public boolean verifyCallback(Map<String, String> params) {
            return true;
        }

        @Override
        public String queryStatus(String payOrderNo) {
            return "SUCCESS";
        }
    }
}
