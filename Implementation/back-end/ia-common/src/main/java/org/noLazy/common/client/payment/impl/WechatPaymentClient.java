package org.noLazy.common.client.payment.impl;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.payment.PaymentClient;
import org.noLazy.common.config.WechatPayProperties;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付 API v3 Native 支付实现 —— 扫码支付（用户扫商户二维码）。
 *
 * <pre>
 * Scenario: 商户发起 Native 扫码支付
 *   Given 已配置商户号、私钥路径、证书序列号、APIv3密钥、回调地址
 *   When 调用 initiatePayment(outTradeNo, amount, description)
 *   Then 调用微信 POST /v3/pay/transactions/native 下单
 *   And 返回 code_url（前端用此 URL 生成二维码）
 *
 * Scenario: 支付成功回调验签
 *   Given 微信支付平台 POST 回调通知到 /api/pay/callback/wechat
 *   And 请求头含 Wechatpay-Signature/Nonce/Timestamp/Serial
 *   When 调用 verifyCallback(params)
 *   Then 使用微信平台证书公钥验证签名
 *   And 签名有效返回 true，无效返回 false
 *
 * Scenario: 未配置商户号时自动降级为桩代码
 *   Given wechat.pay.merchant-id 未配置
 *   Then Spring 自动激活 MockWechatPaymentClient（返回假数据）
 *   And 不影响本地开发调试
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wechat.pay.merchant-id")
public class WechatPaymentClient implements PaymentClient {

    private final WechatPayProperties props;

    private NativePayService nativePayService;
    private RSAAutoCertificateConfig rsaConfig;

    /**
     * 初始化微信支付 SDK：RSA 自动证书管理。
     * SDK 启动时自动下载微信支付平台证书，后续自动更新（无需手动管理证书文件）。
     */
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

    /**
     * 发起 Native 支付。
     * 微信 API: POST https://api.mch.weixin.qq.com/v3/pay/transactions/native
     *
     * @param orderNo     商户订单号（trade-service 的 orderNo）
     * @param amount      金额（单位：分）
     * @param description 商品描述（如"冰美商城订单"）
     * @return code_url 二维码链接，前端据此生成扫码支付二维码
     */
    @Override
    public String initiatePayment(String orderNo, int amount, String description) {
        PrepayRequest request = new PrepayRequest();
        request.setAppid(null);          // Native 支付可不传 appid
        request.setMchid(props.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(orderNo);
        request.setNotifyUrl(props.getNotifyUrl());

        Amount amountObj = new Amount();
        amountObj.setTotal(amount);
        request.setAmount(amountObj);

        try {
            PrepayResponse response = nativePayService.prepay(request);
            log.info("微信支付 Native 下单成功: orderNo={}, codeUrl={}", orderNo, response.getCodeUrl());
            return response.getCodeUrl();
        } catch (Exception e) {
            log.error("微信支付 Native 下单失败: orderNo={}", orderNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "支付下单失败: " + e.getMessage());
        }
    }

    /**
     * 验证微信支付回调签名。
     * 使用微信支付 SDK 的 NotificationParser 验签并解密回调内容。
     *
     * @param params 回调参数 Map，必须包含：
     *               "Wechatpay-Signature"  — HTTP 请求头签名
     *               "Wechatpay-Nonce"      — HTTP 请求头随机数
     *               "Wechatpay-Timestamp"  — HTTP 请求头时间戳
     *               "Wechatpay-Serial"     — HTTP 请求头证书序列号
     *               "body"                 — HTTP 原始请求体 JSON
     * @return true 签名有效
     */
    @Override
    public boolean verifyCallback(Map<String, String> params) {
        try {
            String signature = params.get("Wechatpay-Signature");
            String nonce = params.get("Wechatpay-Nonce");
            String timestamp = params.get("Wechatpay-Timestamp");
            String serial = params.get("Wechatpay-Serial");
            String body = params.get("body");

            if (signature == null || nonce == null || timestamp == null
                    || serial == null || body == null) {
                log.error("微信支付回调参数不完整: signature={}, nonce={}, timestamp={}, serial={}",
                        signature, nonce, timestamp, serial);
                return false;
            }

            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();

            NotificationParser parser = new NotificationParser(rsaConfig);
            // 验签并解析回调内容 — 签名无效时 SDK 会抛出 ValidationException
            parser.parse(requestParam, Object.class);

            log.info("微信支付回调签名验证通过");
            return true;
        } catch (Exception e) {
            log.error("微信支付回调签名验证失败", e);
            return false;
        }
    }

    /**
     * 查询支付单状态。
     * 微信 API: GET https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/{out_trade_no}
     *
     * @param payOrderNo 商户支付单号
     * @return SUCCESS / NOTPAY / CLOSED / REFUND 等，失败返回 null
     */
    @Override
    public String queryStatus(String payOrderNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(payOrderNo);
        request.setMchid(props.getMerchantId());

        try {
            var transaction = nativePayService.queryOrderByOutTradeNo(request);
            String tradeState = transaction.getTradeState().name();
            log.info("微信支付状态查询: payOrderNo={}, state={}", payOrderNo, tradeState);
            return tradeState;
        } catch (Exception e) {
            log.error("微信支付状态查询失败: payOrderNo={}", payOrderNo, e);
            return null;
        }
    }

    // ======================== 桩代码（开发环境自动启用） ========================

    /**
     * Mock 微信支付客户端 —— 未配置 wechat.pay.merchant-id 时自动激活。
     * 所有方法返回模拟数据，不影响本地开发调试。
     */
    @Slf4j
    @Component
    @ConditionalOnProperty(name = "wechat.pay.merchant-id", havingValue = "false", matchIfMissing = true)
    public static class MockWechatPaymentClient implements PaymentClient {

        @Override
        public String initiatePayment(String orderNo, int amount, String description) {
            log.info("===== MOCK WECHAT PAY =====");
            log.info("OrderNo: {}, Amount: {} 分, Desc: {}", orderNo, amount, description);
            return "https://mock-pay.example.com/qrcode?order=" + orderNo;
        }

        @Override
        public boolean verifyCallback(Map<String, String> params) {
            log.info("Mock WeChat callback verification — always passes");
            return true;
        }

        @Override
        public String queryStatus(String payOrderNo) {
            log.info("Mock query pay status for: {}", payOrderNo);
            return "SUCCESS";
        }
    }
}
