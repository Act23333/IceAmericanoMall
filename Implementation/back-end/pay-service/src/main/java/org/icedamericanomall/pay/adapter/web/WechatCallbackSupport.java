package org.icedamericanomall.pay.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信支付 v3 回调的适配层支撑工具（支付回调 PAY2 与退款回调 PAY6 共用）。
 *
 * <p><b>为什么需要它</b>：PAY2 与 PAY6 是两条不同的回调端点（分别由 {@code PayController} 与
 * {@code RefundController} 承接），但「收集原始头 + 逐字节读取报文体 + 按 v3 规范组装裸 JSON 应答」
 * 三段逻辑完全一致。抽到本类避免复制粘贴（并保证两处应答协议严格同构，ADR-029）。
 *
 * <p><b>应答口径（ADR-029）</b>：<b>渠道协议优先于业务信封</b> —— 成功 HTTP 200 +
 * {@code {"code":"SUCCESS","message":"成功"}}；失败 HTTP 500 + {@code {"code":"FAIL","message":...}}
 * （非 200 触发微信重推）。{@code message} 为固定文案，不含内部细节。
 *
 * <p><b>为什么用原始头 + 原始报文体</b>：验签必须对「原始字节序列」计算待签名串，
 * 因此报文体用 {@link StreamUtils#copyToString} 按 UTF-8 原样拷贝，<b>不能</b>用
 * {@code BufferedReader.readLine()} 拼接（会吞掉行分隔符、改变字节序列）；
 * 并且第三方回调字段随渠道版本变化，属 ADR-023「入参必须类型化」的显式例外（ADR-033）。
 */
final class WechatCallbackSupport {

    /** 微信支付 v3 应答 code：处理成功。 */
    static final String CODE_SUCCESS = "SUCCESS";

    /** 微信支付 v3 应答 code：处理失败（渠道将重推）。 */
    static final String CODE_FAIL = "FAIL";

    /** 成功应答文案。 */
    static final String MESSAGE_SUCCESS = "成功";

    /** 失败应答文案（固定，不外泄内部细节）。 */
    static final String MESSAGE_FAIL = "处理失败，请重试";

    private WechatCallbackSupport() {
    }

    /** 读取原始请求头（验签需要签名头原文，头名大小写不敏感的匹配在通道适配层完成）。 */
    static Map<String, String> readHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    /**
     * 读取原始请求体（验签需要<b>逐字节原文</b>，不能交给框架反序列化）。
     *
     * <p>按 UTF-8 原样拷贝字节流，保留行分隔符与全部空白——任何改写都会让待签名串与实际报文不一致。
     */
    static String readRawBody(HttpServletRequest request) throws IOException {
        return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
    }

    /** 成功应答：HTTP 200 + 裸 JSON {@code {"code":"SUCCESS","message":"成功"}}。 */
    static ResponseEntity<Map<String, String>> success() {
        return response(HttpStatus.OK, CODE_SUCCESS, MESSAGE_SUCCESS);
    }

    /** 失败应答：HTTP 500 + 裸 JSON {@code {"code":"FAIL","message":"处理失败，请重试"}}（触发渠道重推）。 */
    static ResponseEntity<Map<String, String>> fail() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, CODE_FAIL, MESSAGE_FAIL);
    }

    /** 组装微信 v3 应答体（固定顺序，便于日志与断言阅读）。 */
    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
