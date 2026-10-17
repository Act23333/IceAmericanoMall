package org.icedamericanomall.integration.payment;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.Verifier;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.billdownload.BillDownloadService;
import com.wechat.pay.java.service.billdownload.model.BillType;
import com.wechat.pay.java.service.billdownload.model.GetTradeBillRequest;
import com.wechat.pay.java.service.billdownload.model.QueryBillEntity;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 微信支付 API v3 通道（Native 扫码 + JSAPI 小程序拉起）—— {@code wechat.pay.merchant-id} 非空且未被
 * {@code wechat.pay.enabled=false} 关闭时激活（与 {@link MockWechatPaymentClient} 互斥）。
 *
 * <p><b>装配口径（V5.3 修正，P1 缺陷）</b>：此前用
 * {@code @ConditionalOnProperty(name = "wechat.pay.merchant-id")} 判定真实实现 ——
 * 该注解只判「属性是否存在」。而 pay-service 的 yml 写作
 * {@code merchant-id: ${WECHAT_PAY_MERCHANT_ID:}}，环境变量未设时属性值为<b>空串</b>：
 * 空串算「存在」→ 真实实现被装配；Mock 侧的 {@code havingValue = "false"} 又不匹配空串
 * → Mock 不装配。结果是「一个 PaymentClient 都没有」或「装配了必然初始化失败的 Real」，
 * 干净环境 pay-service 启动即崩（空 merchantId + 不存在的私钥路径）。
 * 现在改用 {@link WechatPayConditions} 读取属性<b>值</b>并判空，空串可靠落入 Mock 分支；
 * 真实实现只在 merchant-id 真正非空时创建，无需在构造/初始化期做兜底判空。
 *
 * <p><b>推荐配置</b>（pay-service 的 yml 建议，待主控落地）：
 * <pre>
 * wechat:
 *   pay:
 *     enabled: ${WECHAT_PAY_ENABLED:}      # 空=自动（merchant-id 非空即真实支付）；false=强制 Mock
 *     merchant-id: ${WECHAT_PAY_MERCHANT_ID:}
 * </pre>
 * 在 yml 落地前，本类的判定不依赖 {@code wechat.pay.enabled}（缺失即「自动」），
 * 因此行为兼容现有环境变量注入方式。
 *
 * <p>真实实现与真实支付厂商 SDK 强绑定，SDK 不在类路径（如未引入 wechatpay-java 的模块）时
 * 由 {@code @ConditionalOnClass} 跳过，Mock 兜底。
 *
 * <p><b>回调解析（V5.5 修复，P0 断链）</b>：微信 v3 回调的 {@code out_trade_no} 等业务字段位于
 * <b>AES-GCM 解密后</b>的 {@code resource} 明文里，不 HTTP 表单参数。本类负责「验签（SDK
 * {@link Verifier}）→ 解密（{@link WechatCallbackDecoder} 依微信规范实现）→ 提取强类型结果」，
 * 应用层只消费 {@link WechatCallbackResult}（ADR-033）。修复前 pay-service 直接从回调
 * 参数 Map 读 {@code out_trade_no}，微信路径恒为 {@code null} → 支付单查不到 → 回调恒 500 →
 * 微信无限重推且支付永不确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(NativePayService.class)
@Conditional(WechatPayConditions.Real.class)
public class WechatPaymentClient implements PaymentClient {

    /** 微信签名头：平台证书/公钥序列号。 */
    static final String HEADER_SERIAL = "Wechatpay-Serial";
    /** 微信签名头：随机串（参与待签名串构造）。 */
    static final String HEADER_NONCE = "Wechatpay-Nonce";
    /** 微信签名头：时间戳（参与待签名串构造）。 */
    static final String HEADER_TIMESTAMP = "Wechatpay-Timestamp";
    /** 微信签名头：签名值。 */
    static final String HEADER_SIGNATURE = "Wechatpay-Signature";
    /** 微信签名头：签名类型（可选，缺省 WECHATPAY2-SHA256-RSA2048）。 */
    static final String HEADER_SIGN_TYPE = "Wechatpay-Signature-Type";

    /** 微信退款金额币种（人民币，v3 唯一取值）。 */
    static final String CURRENCY_CNY = "CNY";

    /** 账单下载 HTTP 成功状态码。 */
    private static final int HTTP_STATUS_OK = 200;

    private final WechatPayProperties props;
    private NativePayService nativePayService;
    private JsapiServiceExtension jsapiServiceExtension;
    private RefundService refundService;
    private BillDownloadService billDownloadService;
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
        this.jsapiServiceExtension = new JsapiServiceExtension.Builder().config(rsaConfig).build();
        this.refundService = new RefundService.Builder().config(rsaConfig).build();
        this.billDownloadService = new BillDownloadService.Builder().config(rsaConfig).build();
        log.info("微信支付 SDK 初始化完成: merchantId={}, appId={}", props.getMerchantId(), props.getAppId());
    }

    @Override
    public String initiatePayment(String outTradeNo, int amount, String description) {
        PrepayRequest request = new PrepayRequest();
        // V5.18 修复既有缺陷：微信 v3 下单报文必填 appid，此前漏传真实环境必然下单失败
        request.setAppid(props.getAppId());
        request.setMchid(props.getMerchantId());
        request.setDescription(description);
        // 对外渠道交易号（channel_trade_no）—— 不是内部 order_no / pay_order_no（两者已彻底分离）
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(props.getNotifyUrl());
        Amount amountObj = new Amount();
        amountObj.setTotal(amount);
        request.setAmount(amountObj);
        try {
            var response = nativePayService.prepay(request);
            log.info("微信支付 Native 下单: outTradeNo={}", outTradeNo);
            return response.getCodeUrl();
        } catch (Exception e) {
            log.error("微信支付下单失败: outTradeNo={}", outTradeNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "支付下单失败: " + e.getMessage());
        }
    }

    /**
     * 发起微信 JSAPI 下单（小程序内拉起支付，PAY7）：{@code payer.openid} 与商户下单 appid
     * 绑定（appid 取自 {@link WechatPayProperties#getAppId()}，必须与 openid 同源），SDK
     * {@link JsapiServiceExtension#prepayWithRequestPayment} 同步返回带签名的拉起参数集。
     *
     * <p>微信支付结果不经本方法返回，仍由 PAY2 支付回调确认（与 Native 通道共用同一链路）。
     *
     * @param outTradeNo  对外渠道交易号（微信 {@code out_trade_no}，取自 {@code pay_order.channel_trade_no}）
     * @param amount      支付金额（分）
     * @param description 商品描述
     * @param openid      用户在下单 appid 下的 openid
     * @return 小程序拉起参数集（appId/timeStamp/nonceStr/packageVal/signType/paySign）
     * @throws BizException {@code BUSINESS_EXECUTION_EXCEPTION} 渠道拒绝 / 调用失败
     */
    @Override
    public JsapiPayResult initiateJsapiPayment(String outTradeNo, int amount, String description, String openid) {
        var request = new com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest();
        request.setAppid(props.getAppId());
        request.setMchid(props.getMerchantId());
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(props.getNotifyUrl());
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        var amountObj = new com.wechat.pay.java.service.payments.jsapi.model.Amount();
        amountObj.setTotal(amount);
        request.setAmount(amountObj);
        try {
            var response = jsapiServiceExtension.prepayWithRequestPayment(request);
            log.info("微信支付 JSAPI 下单: outTradeNo={}", outTradeNo);
            return toJsapiPayResult(response);
        } catch (Exception e) {
            log.error("微信 JSAPI 下单失败: outTradeNo={}", outTradeNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "JSAPI 支付下单失败: " + e.getMessage());
        }
    }

    /**
     * SDK 拉起参数集 → 通道无关结果（六字段一一对应，{@code packageVal} 沿用 SDK 原名，
     * 规避 Java 关键字 {@code package}；独立成静态方法以便纯单测映射口径）。
     */
    static JsapiPayResult toJsapiPayResult(PrepayWithRequestPaymentResponse response) {
        return new JsapiPayResult(response.getAppId(), response.getTimeStamp(), response.getNonceStr(),
                response.getPackageVal(), response.getSignType(), response.getPaySign());
    }

    /**
     * 解析微信支付 v3 回调：验签 → AES-GCM 解密 {@code resource} → 提取强类型结果（ADR-033）。
     *
     * <p>验签复用 SDK 的 {@link Verifier}（待签名串 = {@code timestamp\nnonce\nbody\n}），
     * 解密走 {@link WechatCallbackDecoder} 的微信规范实现（APIv3 密钥取自
     * {@link WechatPayProperties#getApiV3Key()}）。
     *
     * @param headers HTTP 请求头（{@code Wechatpay-*} 大小写不敏感）
     * @param rawBody 回调原始报文
     * @return 强类型解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / 报文非法 / 解密失败
     */
    @Override
    public WechatCallbackResult parseWechatCallback(Map<String, String> headers, String rawBody) {
        verifyWechatSignature(headers, rawBody);
        WechatCallbackResult result = WechatCallbackDecoder.decode(props.getApiV3Key(), rawBody);
        log.info("微信回调解析成功: outTradeNo={}, tradeState={}, transactionId={}",
                result.outTradeNo(), result.rawState(), result.channelTradeNo());
        return result;
    }

    /**
     * 解析微信支付 v3 <b>退款</b>回调：与支付回调共用验签基建，解密后提取退款字段（F1）。
     *
     * @param headers HTTP 请求头（{@code Wechatpay-*} 大小写不敏感）
     * @param rawBody 回调原始报文
     * @return 强类型退款解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / 报文非法 / 解密失败 / 缺 out_refund_no
     */
    @Override
    public WechatRefundCallbackResult parseWechatRefundCallback(Map<String, String> headers, String rawBody) {
        verifyWechatSignature(headers, rawBody);
        WechatRefundCallbackResult result = WechatCallbackDecoder.decodeRefund(props.getApiV3Key(), rawBody);
        log.info("微信退款回调解析成功: refundNo={}, refundStatus={}, refundId={}",
                result.outRefundNo(), result.rawRefundStatus(), result.refundId());
        return result;
    }

    /**
     * 微信 v3 回调验签（支付 / 退款共用）：待签名串 = {@code timestamp\nnonce\nbody\n}，逐字节原文。
     *
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败或 SDK 校验异常（渠道将按策略重推）
     */
    private void verifyWechatSignature(Map<String, String> headers, String rawBody) {
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(header(headers, HEADER_SERIAL))
                .nonce(header(headers, HEADER_NONCE))
                .signature(header(headers, HEADER_SIGNATURE))
                .timestamp(header(headers, HEADER_TIMESTAMP))
                .signType(header(headers, HEADER_SIGN_TYPE))
                .body(rawBody)
                .build();
        try {
            if (!rsaConfig.createVerifier().verify(requestParam.getSerialNumber(),
                    requestParam.getMessage(), requestParam.getSignature())) {
                throw new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调验签失败");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // SDK 抛 ValidationException / 证书未就绪等 —— 一律按验签失败处理（渠道将按策略重推）
            log.error("微信回调验签异常: serial={}", requestParam.getSerialNumber(), e);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调验签失败");
        }
    }

    /**
     * 发起微信 v3 退款（F1）。
     *
     * <p><b>金额口径</b>：微信 {@code amount.total} 必须等于原支付单金额、{@code amount.refund}
     * 才是本次退款额（部分退款靠两者不等表达），否则渠道直接拒绝。
     * <b>幂等</b>：{@code out_refund_no} 是渠道侧幂等键，重试必须复用同一 {@code outRefundNo}
     * （重复提交同号返回原退款单，不会重复出款）。
     * <b>寻址</b>：定位原支付单的是 {@code out_trade_no}（= 我方 {@code channel_trade_no}），
     * 不是内部 {@code pay_order_no}。
     *
     * @return 渠道受理结果；微信同步返回 {@code SUCCESS} 时即 {@code settled=true}，
     *         {@code PROCESSING} 时为 {@code settled=false}（等 PAY6 回调确认）
     * @throws BizException {@code BUSINESS_EXECUTION_EXCEPTION} 渠道拒绝 / 调用失败
     */
    @Override
    public ChannelRefundResult refund(String outTradeNo, String outRefundNo, int refundAmount, int orderAmount,
                                      String reason) {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(outTradeNo);
        request.setOutRefundNo(outRefundNo);
        request.setReason(reason);
        request.setNotifyUrl(props.getRefundNotifyUrl());
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund((long) refundAmount);
        amountReq.setTotal((long) orderAmount);
        amountReq.setCurrency(CURRENCY_CNY);
        request.setAmount(amountReq);
        try {
            Refund refund = refundService.create(request);
            String channelRefundId = refund.getRefundId();
            boolean settled = Status.SUCCESS == refund.getStatus();
            log.info("微信退款已受理: outRefundNo={}, outTradeNo={}, refundAmount={}, status={}, refundId={}",
                    outRefundNo, outTradeNo, refundAmount, refund.getStatus(), channelRefundId);
            return new ChannelRefundResult(channelRefundId, settled);
        } catch (Exception e) {
            log.error("微信退款申请失败: outRefundNo={}, outTradeNo={}, refundAmount={}",
                    outRefundNo, outTradeNo, refundAmount, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信退款申请失败");
        }
    }

    /**
     * 按商户退款单号查询微信 v3 退款终态（悬挂退款补偿任务用，ADR-035 风险收敛）。
     *
     * <p>对应微信 v3 {@code GET /v3/refund/domestic/refunds/{out_refund_no}}，走 SDK
     * {@link RefundService#queryByOutRefundNo(QueryByOutRefundNoRequest)}；{@code refund_status}
     * 在本适配层归一化（SUCCESS→SUCCESS，CLOSED/ABNORMAL→FAILED，PROCESSING→PROCESSING），
     * 调用方不接触微信原始状态串。
     *
     * @param outRefundNo 对外退款项号（微信 {@code out_refund_no}，重试复用同号）
     * @return 强类型查询结果
     * @throws BizException {@code BUSINESS_EXECUTION_EXCEPTION} 查询调用失败（不允许吞掉伪装成「仍在处理」）
     */
    @Override
    public ChannelRefundQueryResult queryRefund(String outRefundNo) {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);
        try {
            Refund refund = refundService.queryByOutRefundNo(request);
            ChannelRefundQueryResult result =
                    new ChannelRefundQueryResult(refund.getRefundId(), toChannelRefundState(refund.getStatus()));
            log.info("微信退款查询成功: outRefundNo={}, refundId={}, refundStatus={}",
                    outRefundNo, result.refundId(), refund.getStatus());
            return result;
        } catch (Exception e) {
            log.error("微信退款查询失败: outRefundNo={}", outRefundNo, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "微信退款查询失败");
        }
    }

    /**
     * 微信 {@code refund_status} → 归一化三值状态（渠道协议知识收口在本适配层）。
     *
     * @param status 微信 SDK 退款状态（枚举封闭集，四种取值全覆盖）
     * @return 归一化状态
     */
    static ChannelRefundState toChannelRefundState(Status status) {
        return switch (status) {
            case SUCCESS -> ChannelRefundState.SUCCESS;
            case CLOSED, ABNORMAL -> ChannelRefundState.FAILED;
            case PROCESSING -> ChannelRefundState.PROCESSING;
        };
    }

    /**
     * 下载微信 v3 交易账单并解析为对账条目（F4）。
     *
     * <p>账单未生成（如账单日当天）或下载失败时返回<b>空列表</b>并告警 —— 对账编排据此落
     * 「批次失败/零渠道流水」，由管理端重跑，不应让对账线程崩溃。
     */
    @Override
    public List<ChannelBillItem> billDownload(String billDate) {
        try {
            GetTradeBillRequest request = new GetTradeBillRequest();
            request.setBillDate(billDate);
            request.setBillType(BillType.SUCCESS);
            QueryBillEntity entity = billDownloadService.getTradeBill(request);
            String downloadUrl = entity == null ? null : entity.getDownloadUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                log.warn("微信账单未生成或无下载地址: billDate={}", billDate);
                return List.of();
            }
            List<ChannelBillItem> items = WechatBillCsvParser.parse(fetchBillFile(downloadUrl));
            log.info("微信账单下载解析完成: billDate={}, rows={}", billDate, items.size());
            return items;
        } catch (Exception e) {
            log.error("微信账单下载失败: billDate={}", billDate, e);
            return List.of();
        }
    }

    /**
     * 拉取账单文件正文（账单 {@code download_url} 自带临时令牌，无需再带签名头）。
     *
     * @param downloadUrl 账单下载地址
     * @return 账单文本
     * @throws BizException {@code BUSINESS_EXECUTION_EXCEPTION} 非 200 / IO 失败
     */
    private String fetchBillFile(String downloadUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != HTTP_STATUS_OK) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                        "账单下载失败，HTTP " + response.statusCode());
            }
            return response.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("账单下载 IO 异常: url={}", downloadUrl, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "账单下载失败");
        }
    }

    @Override
    public String queryStatus(String outTradeNo) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(outTradeNo);
        request.setMchid(props.getMerchantId());
        try {
            return nativePayService.queryOrderByOutTradeNo(request).getTradeState().name();
        } catch (Exception e) {
            log.error("微信支付状态查询失败: outTradeNo={}", outTradeNo, e);
            return null;
        }
    }

    /**
     * Mock 微信支付 —— {@code merchant-id} 为空或 {@code wechat.pay.enabled=false} 时激活
     * （与 {@link WechatPaymentClient} 严格互补，保证任何配置下恰好一个 {@code PaymentClient}）。
     *
     * <p>回调口径与真实实现对齐：Mock 环境没有真实密文/签名，直接把 body 当<b>明文 JSON</b>
     * 解析（{@code out_trade_no} + {@code trade_state} 或 {@code result_code}），
     * 成功判定与字段提取复用 {@link WechatCallbackDecoder#extract(String)}，
     * 因而回调分支在 Mock/Real 下行为一致（body 非 JSON → 失败标记）。
     */
    @Slf4j
    @Component
    @Conditional(WechatPayConditions.Mock.class)
    public static class MockWechatPaymentClient implements PaymentClient {

        /** Mock 渠道退款单号前缀（形如 {@code mock-rfd-<refundNo>}）。 */
        static final String MOCK_CHANNEL_REFUND_NO_PREFIX = "mock-rfd-";

        /** Mock 渠道交易号前缀（形如 {@code mock-txn-<billDate>-<n>}）。 */
        static final String MOCK_CHANNEL_TXN_NO_PREFIX = "mock-txn-";

        /** Mock 商户 appid（拉起参数集 appId 字段；Mock 环境不校验与 openid 的同源性）。 */
        static final String MOCK_APP_ID = "wx-mock-appid";

        /** Mock JSAPI 预支付标识前缀（packageVal 形如 {@code prepay_id=mock-<payOrderNo>}）。 */
        static final String MOCK_PREPAY_ID_PREFIX = "prepay_id=mock-";

        /** Mock 拉起参数集签名类型（与微信 v3 真实取值一致）。 */
        static final String MOCK_SIGN_TYPE = "RSA";

        @Override
        public String initiatePayment(String outTradeNo, int amount, String description) {
            log.info("===== MOCK WECHAT PAY ===== OutTradeNo: {}, Amount: {} 分", outTradeNo, amount);
            return "https://mock-pay.example.com/qrcode?order=" + outTradeNo;
        }

        /**
         * Mock JSAPI 下单：返回形态与真实一致的六字段拉起参数集
         * （packageVal 沿用 {@code prepay_id=} 协议前缀，签名值为占位串），
         * 供本地打通「PAY7 下单 → 前端拉起 → PAY2 回调确认」链路。
         */
        @Override
        public JsapiPayResult initiateJsapiPayment(String outTradeNo, int amount, String description, String openid) {
            log.info("===== MOCK WECHAT JSAPI ===== OutTradeNo: {}, Amount: {} 分", outTradeNo, amount);
            return new JsapiPayResult(MOCK_APP_ID, String.valueOf(System.currentTimeMillis() / 1000),
                    "mock-nonce", MOCK_PREPAY_ID_PREFIX + outTradeNo, MOCK_SIGN_TYPE, "mock-pay-sign");
        }

        /**
         * Mock 口径：忽略签名头，直接按明文 JSON 解析（{@code trade_state} 优先，
         * 回落 {@code result_code}）。body 非 JSON / 缺 {@code out_trade_no} 时抛
         * {@link BizException}({@code ILLEGAL_REQUEST})，与真实实现的失败标记口径一致。
         */
        @Override
        public WechatCallbackResult parseWechatCallback(Map<String, String> headers, String rawBody) {
            WechatCallbackResult result = WechatCallbackDecoder.extract(rawBody);
            log.info("[MockWechat] 回调解析: payOrderNo={}, tradeState={}", result.outTradeNo(), result.rawState());
            return result;
        }

        /**
         * Mock 口径：忽略签名头，直接按明文 JSON 提取退款字段（{@code out_refund_no} +
         * {@code refund_status}），与真实解密路径的提取口径一致。
         */
        @Override
        public WechatRefundCallbackResult parseWechatRefundCallback(Map<String, String> headers, String rawBody) {
            WechatRefundCallbackResult result = WechatCallbackDecoder.extractRefund(rawBody);
            log.info("[MockWechat] 退款回调解析: refundNo={}, refundStatus={}",
                    result.outRefundNo(), result.rawRefundStatus());
            return result;
        }

        /**
         * Mock 退款：受理即返回模拟渠道退款单号，{@code settled=false}（处理中）——
         * 与微信真实路径的多数场景一致：退款成功由退款回调（PAY6）确认，而非同步返回。
         * 便于在 Mock 环境验证「处理中 → 回调置成功 → 回写售后单」的完整链路。
         */
        @Override
        public ChannelRefundResult refund(String outTradeNo, String outRefundNo, int refundAmount, int orderAmount,
                                          String reason) {
            log.info("===== MOCK WECHAT REFUND ===== outTradeNo={}, outRefundNo={}, refundAmount={}, orderAmount={}",
                    outTradeNo, outRefundNo, refundAmount, orderAmount);
            return ChannelRefundResult.processing(MOCK_CHANNEL_REFUND_NO_PREFIX + outRefundNo);
        }

        /** Mock 账单：返回样例交易流水，供本地打通「下载 → 比对 → 落差异明细」链路。 */
        @Override
        public List<ChannelBillItem> billDownload(String billDate) {
            List<ChannelBillItem> items = List.of(
                    new ChannelBillItem("MOCK-PAY-0001", MOCK_CHANNEL_TXN_NO_PREFIX + billDate + "-1", 9900),
                    new ChannelBillItem("MOCK-PAY-0002", MOCK_CHANNEL_TXN_NO_PREFIX + billDate + "-2", 12800));
            log.info("[MockWechat] 账单下载: billDate={}, rows={}（样例账单）", billDate, items.size());
            return items;
        }

        /**
         * Mock 退款查询：恒返回「处理中」——与 {@link #refund} 的受理口径一致（受理后等回调确认），
         * Mock 环境不模拟渠道侧异步推进，真实终态由人工回调 / 测试桩驱动；
         * 保证悬挂补偿任务在 Mock 环境可空跑（查询不报错、不改本地状态）。
         */
        @Override
        public ChannelRefundQueryResult queryRefund(String outRefundNo) {
            log.info("[MockWechat] 退款查询: outRefundNo={}, refundStatus=PROCESSING", outRefundNo);
            return new ChannelRefundQueryResult(MOCK_CHANNEL_REFUND_NO_PREFIX + outRefundNo,
                    ChannelRefundState.PROCESSING);
        }

        @Override
        public String queryStatus(String outTradeNo) {
            return "SUCCESS";
        }
    }

    /** 取请求头（HTTP 头名大小写不敏感；缺失返回 {@code null}，SDK 会在缺失时报验签失败）。 */
    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
