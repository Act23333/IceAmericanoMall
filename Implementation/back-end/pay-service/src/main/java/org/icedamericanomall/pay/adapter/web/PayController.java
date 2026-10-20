package org.icedamericanomall.pay.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.adapter.request.JsapiPayReq;
import org.icedamericanomall.pay.adapter.response.JsapiPayParamsVO;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.app.ChannelCallbackManager;
import org.icedamericanomall.pay.app.PayManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付 HTTP 入口（发起支付 / 渠道回调 / 状态查询）。
 *
 * <p><b>回调应答口径（V5.4 修复，P1 掉单风险）</b>：<b>渠道协议优先于业务信封</b>——
 * 第三方支付回调的应答体是被渠道方解析的协议报文，不能用平台统一 {@code Result} 信封包裹，
 * 否则渠道判定「应答不合规」并按失败处理（微信对该笔通知停止重试 → 掉单）。
 * <ul>
 *   <li>{@code /callback/wechat} —— 按微信支付 v3 规范应答（200 + SUCCESS / 500 + FAIL，见
 *       {@link WechatCallbackSupport}）。</li>
 *   <li>{@code /callback/alipay} —— 返回裸字符串 {@code success}（支付宝异步通知协议要求正文恰为此串）。</li>
 *   <li>回调入参用 {@code Map}/{@code HttpServletRequest} 而非类型化 {@code *Req}：
 *       第三方回调需对<b>原始报文与签名头</b>验签（字段随渠道版本变化），属规范中
 *       「@RequestBody 必须类型化」的合理例外，保持。</li>
 *   <li><b>回调业务字段不在控制器组装</b>（ADR-033）：微信 v3 的 {@code out_trade_no} /
 *       {@code trade_state} 位于 AES-GCM 解密后的 {@code resource} 报文里，控制器只把
 *       <b>原始请求头 + 原始报文体</b>透传给 {@link PayManager}，验签/解密/提取统一由通道适配层完成。</li>
 * </ul>
 *
 * <p><b>退款回调（PAY6）不在本类</b>：退款是独立用例族（ADR-025），由
 * {@link RefundController} 承接（{@code /api/pay/callback/wechat-refund}）。
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    /** 微信支付 v3 应答 code：处理成功（别名，权威定义见 {@link WechatCallbackSupport}）。 */
    public static final String RESPONSE_CODE_SUCCESS = WechatCallbackSupport.CODE_SUCCESS;

    /** 微信支付 v3 应答 code：处理失败（渠道将重推）。 */
    public static final String RESPONSE_CODE_FAIL = WechatCallbackSupport.CODE_FAIL;

    /** 成功应答文案。 */
    static final String RESPONSE_MESSAGE_SUCCESS = WechatCallbackSupport.MESSAGE_SUCCESS;

    /** 失败应答文案（固定，不外泄内部细节）。 */
    static final String RESPONSE_MESSAGE_FAIL = WechatCallbackSupport.MESSAGE_FAIL;

    private final PayManager payManager;
    private final ChannelCallbackManager callbackManager;

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
     * 小程序内拉起支付（微信 JSAPI 通道，PAY7）：JSAPI 下单（{@code payer.openid} 与
     * {@code wechat.pay.app-id} 绑定）返回<b>带签名</b>拉起参数集，前端直传
     * {@code wx.requestPayment}；支付结果经 {@code /callback/wechat}（PAY2）确认，本端点不推进状态。
     *
     * @param req 订单号 + 用户在下单 appid 下的 openid（均必填，同源约束由配置侧保证）
     * @return 拉起参数集 appId/timeStamp/nonceStr/packageVal/signType/paySign
     */
    @PostMapping("/wechat/jsapi")
    public Result<JsapiPayParamsVO> initiateJsapi(@Valid @RequestBody JsapiPayReq req) {
        Long userId = UserContext.getUserId();
        return Result.ok(payManager.initiateJsapiPayment(req.getOrderNo(), userId, req.getOpenid()));
    }

    /**
     * 微信支付回调通知（微信支付 API v3 规范应答）。
     *
     * <p>控制器<b>只做搬运</b>：把原始请求头与原始报文体原样交给 {@link ChannelCallbackManager}，由通道适配层
     * 完成验签、AES-GCM 解密与业务字段提取（ADR-033）——回调业务字段（{@code out_trade_no} 等）
     * 位于解密后的报文中，控制器不得、也无法在参数层组装语义键。
     *
     * <p>应答体为裸 {@code code/message} JSON（<b>不</b>包 {@code Result} 信封），见
     * {@link WechatCallbackSupport}。重复通知命中幂等分支时仍返回 {@code SUCCESS}，
     * 避免微信把「已处理过的通知」当失败无限重推。
     *
     * @param request 原始回调请求（签名头 + 报文体）
     * @return 微信 v3 规范的应答体与状态码
     */
    @PostMapping("/callback/wechat")
    public ResponseEntity<Map<String, String>> wechatCallback(HttpServletRequest request) {
        try {
            callbackManager.handleWechatCallback(WechatCallbackSupport.readHeaders(request),
                    WechatCallbackSupport.readRawBody(request));
        } catch (Exception e) {
            // 验签失败 / 解密失败 / 报文不可读 / 业务处理失败 —— 统一 FAIL + 500，让渠道重推
            log.error("微信支付回调处理失败，应答 FAIL: {}", e.getMessage(), e);
            return WechatCallbackSupport.fail();
        }
        return WechatCallbackSupport.success();
    }

    /**
     * 支付宝支付回调（表单参数，含 out_trade_no）。Mock 环境验签恒通过。
     * 应答体为裸字符串 {@code success}（支付宝协议要求正文恰为该串），不用 {@code Result} 包裹。
     */
    @PostMapping("/callback/alipay")
    public String alipayCallback(@RequestParam Map<String, String> params) {
        callbackManager.handleAlipayCallback(params);
        return "success";
    }

    @GetMapping("/order/{orderNo}/status")
    public Result<PayOrderVO> status(@PathVariable String orderNo) {
        return Result.ok(payManager.queryStatus(orderNo));
    }
}

