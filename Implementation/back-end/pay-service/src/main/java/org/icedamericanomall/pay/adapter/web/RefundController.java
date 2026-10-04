package org.icedamericanomall.pay.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.pay.app.RefundManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 退款 HTTP 入口（F1）—— 当前仅微信退款结果通知（PAY6）。
 *
 * <p><b>为什么与 {@code PayController} 分开</b>：退款是独立的用例族（ADR-025），
 * 由 {@link RefundManager} 编排；回调应答协议与支付回调一致，但业务字段域不同
 * （{@code out_refund_no} / {@code refund_status}），分开后两条链路的控制器都保持「只搬运」。
 *
 * <p><b>应答口径（ADR-029 / ADR-033）</b>：控制器只把原始请求头 + 原始报文体透传给
 * {@link RefundManager}，验签/AES-GCM 解密/字段提取全部在通道适配层完成；
 * 应答为裸 {@code code/message} JSON（不包 {@code Result} 信封）：成功 200 + SUCCESS，
 * 失败 500 + FAIL（触发渠道重推），幂等重复通知命中仍 200 + SUCCESS。
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class RefundController {

    private final RefundManager refundManager;

    /**
     * 微信退款结果通知（微信支付 API v3 规范应答）。
     *
     * @param request 原始回调请求（签名头 + 报文体）
     * @return 微信 v3 规范的应答体与状态码
     */
    @PostMapping("/callback/wechat-refund")
    public ResponseEntity<Map<String, String>> wechatRefundCallback(HttpServletRequest request) {
        try {
            refundManager.handleWechatRefundCallback(WechatCallbackSupport.readHeaders(request),
                    WechatCallbackSupport.readRawBody(request));
        } catch (Exception e) {
            // 验签失败 / 解密失败 / 报文不可读 / 业务处理失败 —— 统一 FAIL + 500，让渠道重推
            log.error("微信退款回调处理失败，应答 FAIL: {}", e.getMessage(), e);
            return WechatCallbackSupport.fail();
        }
        return WechatCallbackSupport.success();
    }
}
