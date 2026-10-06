package org.icedamericanomall.integration.payment;

import java.util.List;
import java.util.Map;

/**
 * 支付客户端接口 — 微信支付 Native / JSAPI 抽象。
 *
 * <p><b>回调口径（ADR-033）</b>：微信 v3 回调的<b>验签与解密都在本适配层完成</b>，
 * 对外只暴露 {@link #parseWechatCallback(Map, String)} /
 * {@link #parseWechatRefundCallback(Map, String)} 两个入口，返回强类型结果
 * （{@link WechatCallbackResult} / {@link WechatRefundCallbackResult}）。调用方
 * （pay-service 应用层）<b>不得</b>再从回调参数里按字符串键取 {@code out_trade_no} /
 * {@code trade_state} / {@code out_refund_no} / {@code refund_status}——
 * 那些字段位于 AES-GCM 解密后的报文中，Key 值契约在适配层之外是无效知识。
 *
 * <p><b>退款与对账（F1 / F4）</b>：退款发起（{@link #refund}）、退款回调解析
 * （{@link #parseWechatRefundCallback}）、渠道退款查询（{@link #queryRefund}，悬挂补偿）、
 * 渠道账单下载（{@link #billDownload}）同样收口在本接口——渠道协议知识
 * （退款 API 路径、账单列序、金额计价单位、原始状态串）不越过适配层边界。
 */
public interface PaymentClient {

    /** 发起支付，返回二维码链接 / 支付链接 */
    String initiatePayment(String orderNo, int amount, String description);

    /**
     * 发起微信 JSAPI 下单（小程序内拉起支付，PAY7）：{@code payer.openid} 与商户下单 appid
     * 绑定（appid 取自适配层配置 {@code wechat.pay.app-id}，<b>必须与 openid 同源</b>——
     * 小程序场景即小程序 appid），SDK {@code prepayWithRequestPayment} 同步返回
     * <b>带签名</b>的拉起参数集，前端直传 {@code wx.requestPayment}。
     *
     * <p>支付结果不经本方法返回：仍由 {@link #parseWechatCallback}（PAY2 回调）确认，
     * 与 Native 通道共用同一套回调确认链路。
     *
     * @param orderNo     业务订单号（微信 {@code out_trade_no}）
     * @param amount      支付金额（<b>分</b>）
     * @param description 商品描述
     * @param openid      用户在<b>下单 appid</b> 下的 openid
     * @return 小程序拉起参数集（appId/timeStamp/nonceStr/packageVal/signType/paySign）
     * @throws org.icedamericanomall.common.exception.BizException
     *         {@code BUSINESS_EXECUTION_EXCEPTION} —— 渠道拒绝 / 调用失败（下单失败不允许静默丢失）
     */
    JsapiPayResult initiateJsapiPayment(String orderNo, int amount, String description, String openid);

    /**
     * 解析微信支付 v3 回调：验签 → AES-GCM 解密 {@code resource} → 提取强类型结果。
     *
     * @param headers HTTP 请求头（微信签名头 {@code Wechatpay-*}，大小写不敏感）
     * @param rawBody 回调<b>原始</b>报文体（验签必须用原始字节序列，不能交给框架反序列化）
     * @return 强类型解析结果（含支付单号、成功标记、渠道交易号、原始交易状态）
     * @throws org.icedamericanomall.common.exception.BizException
     *         {@code ILLEGAL_REQUEST} —— 验签失败 / 报文非法 / 解密失败 / 缺 {@code out_trade_no}
     */
    WechatCallbackResult parseWechatCallback(Map<String, String> headers, String rawBody);

    /**
     * 解析微信支付 v3 <b>退款</b>回调：验签 → AES-GCM 解密 → 提取
     * {@code out_refund_no} / {@code refund_id} / {@code refund_status}（复用支付回调同一套验签与解密基建）。
     *
     * @param headers HTTP 请求头（微信签名头 {@code Wechatpay-*}）
     * @param rawBody 回调原始报文体
     * @return 强类型退款解析结果
     * @throws org.icedamericanomall.common.exception.BizException
     *         {@code ILLEGAL_REQUEST} —— 验签失败 / 报文非法 / 解密失败 / 缺 {@code out_refund_no}
     */
    WechatRefundCallbackResult parseWechatRefundCallback(Map<String, String> headers, String rawBody);

    /**
     * 发起渠道退款（原路退回）。
     *
     * <p>退款通知地址由适配层自身的配置（{@code wechat.pay.refund-notify-url}）提供，
     * 不由调用方传入——避免渠道协议配置从应用层穿透到适配层。
     *
     * <p>{@code orderAmount} 是渠道协议要求的<b>原订单（支付）金额</b>：微信 v3 退款接口
     * 的 {@code amount.total} 必须等于原支付单金额、{@code amount.refund} 才是本次退款额，
     * 两者不等时渠道直接拒绝。故调用方需把支付单金额一并传入（支持部分退款）。
     *
     * @param payOrderNo   原支付单号（微信 {@code out_trade_no}）
     * @param refundNo     退款单号（微信 {@code out_refund_no}；渠道侧幂等键，重试复用同号）
     * @param refundAmount 本次退款金额（<b>分</b>）
     * @param orderAmount  原支付单金额（<b>分</b>）
     * @param reason       退款原因摘要（可空）
     * @return 渠道受理结果（{@code settled=false} = 处理中，等退款回调确认终态）
     * @throws org.icedamericanomall.common.exception.BizException
     *         {@code BUSINESS_EXECUTION_EXCEPTION} —— 渠道拒绝 / 调用失败（退款发起不允许静默丢失）
     */
    ChannelRefundResult refund(String payOrderNo, String refundNo, int refundAmount, int orderAmount,
                               String reason);

    /**
     * 按商户退款单号主动查询渠道退款终态（悬挂退款补偿任务用，ADR-035 风险收敛）。
     *
     * <p>与 PAY6 回调互为冗余：回调丢失/未达时，由补偿任务凭本查询收敛退款单终态。
     * 渠道原始状态串在本适配层归一化为 {@link ChannelRefundState} 三值语义，调用方不感知渠道协议细节。
     *
     * @param refundNo 商户退款单号（微信 {@code out_refund_no}，重试复用同号）
     * @return 强类型查询结果（渠道退款单号 + 归一化状态）
     * @throws UnsupportedOperationException 当前渠道不支持退款查询（default 实现；支付宝等无查询 API 的渠道）
     * @throws org.icedamericanomall.common.exception.BizException
     *         {@code BUSINESS_EXECUTION_EXCEPTION} —— 查询调用失败（网络/渠道错误，不允许吞掉伪装成「仍在处理」）
     */
    default ChannelRefundQueryResult queryRefund(String refundNo) {
        throw new UnsupportedOperationException("当前渠道不支持退款查询: " + refundNo);
    }

    /**
     * 下载并解析渠道对账账单（F4）。
     *
     * <p>返回的 {@link ChannelBillItem#amount()} 一律为<b>分</b>（渠道账单以元计价，
     * 元→分转换在适配层内部完成，不向上泄露小数）。
     *
     * @param billDate 账单日（{@code yyyy-MM-dd}）
     * @return 账单条目（仅交易成功流水）；账单未生成 / 下载失败时返回空列表并告警，
     *         不抛异常——对账缺账单应落"批次失败"由管理端重跑，不应让调用方崩溃
     */
    List<ChannelBillItem> billDownload(String billDate);

    /** 查询支付单状态 */
    String queryStatus(String payOrderNo);
}

