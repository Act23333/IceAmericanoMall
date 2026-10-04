package org.icedamericanomall.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 微信支付 v3 回调报文解码器 —— 把渠道原始报文（外层 JSON + AES-GCM 加密的 {@code resource}）
 * 解码成 {@link WechatCallbackResult}（ADR-033）。
 *
 * <p><b>微信 v3 回调报文结构</b>
 * <pre>
 * {
 *   "id": "...", "event_type": "TRANSACTION.SUCCESS",
 *   "resource": { "algorithm": "AEAD_AES_256_GCM",
 *                 "ciphertext": "base64(密文 ‖ 16 字节认证标签)",
 *                 "nonce": "12 字符 IV", "associated_data": "AAD" }
 * }
 * 解密后的明文 JSON：{ "out_trade_no": "...", "transaction_id": "...", "trade_state": "SUCCESS", ... }
 * </pre>
 *
 * <p><b>解密规范（以微信支付 API v3 官方规范为准）</b>：
 * <ul>
 *   <li>算法 {@code AES-256-GCM}、转换名 {@code AES/GCM/NoPadding}、认证标签 128 bit；</li>
 *   <li>密钥 = APIv3 密钥（{@link WechatPayProperties#getApiV3Key()}）的 UTF-8 字节（32 字节）；</li>
 *   <li>IV = {@code resource.nonce} 的 UTF-8 字节（12 字节，微信固定 12 字符）；</li>
 *   <li>AAD = {@code resource.associated_data} 的 UTF-8 字节（可能为空串）；</li>
 *   <li>密文 = {@code Base64.decode(resource.ciphertext)}，其形态为 <b>{@code 密文 ‖ 认证标签}</b> ——
 *       JCA 的 {@code doFinal} 期望的正是这一形态，可直接投喂，<b>不要</b>按
 *       {@code ia-common} 的 {@code AesGcmUtil} 口径（{@code base64(iv ‖ ct ‖ tag)}，IV 内嵌）解读。</li>
 * </ul>
 *
 * <p><b>Mock 口径</b>：Mock 环境没有真实密文，{@link WechatPaymentClient.MockWechatPaymentClient}
 * 直接把回调 body 当普通 JSON 调 {@link #extract(String)}，因此成功判定与字段提取路径
 * 与真实解密后完全一致（{@code trade_state} 优先，缺省回落 {@code result_code}）。
 *
 * <p>任何解析/解密失败一律抛 {@link BizException}({@code ILLEGAL_REQUEST})——渠道回调以
 * 非 200 应答触发重推（ADR-029）。
 */
@Slf4j
final class WechatCallbackDecoder {

    /** 外层报文节点：加密资源。 */
    static final String FIELD_RESOURCE = "resource";
    /** resource 节点字段：Base64 密文（密文 ‖ 认证标签）。 */
    static final String FIELD_CIPHERTEXT = "ciphertext";
    /** resource 节点字段：IV（12 字符）。 */
    static final String FIELD_NONCE = "nonce";
    /** resource 节点字段：附加认证数据 AAD。 */
    static final String FIELD_ASSOCIATED_DATA = "associated_data";
    /** 明文报文字段：商户订单号（本平台支付单号）。 */
    static final String FIELD_OUT_TRADE_NO = "out_trade_no";
    /** 明文报文字段：渠道交易号。 */
    static final String FIELD_TRANSACTION_ID = "transaction_id";
    /** 明文报文字段：微信交易状态。 */
    static final String FIELD_TRADE_STATE = "trade_state";
    /** 明文报文字段：交易状态（Mock / 支付宝口径的回落键）。 */
    static final String FIELD_RESULT_CODE = "result_code";
    /** 明文报文字段：商户退款单号（退款回调；本平台语义下的 {@code refund_order.refund_no}）。 */
    static final String FIELD_OUT_REFUND_NO = "out_refund_no";
    /** 明文报文字段：渠道退款单号（退款回调）。 */
    static final String FIELD_REFUND_ID = "refund_id";
    /** 明文报文字段：微信退款状态（退款回调）。 */
    static final String FIELD_REFUND_STATUS = "refund_status";
    /** 成功终态取值（微信 {@code trade_state} / {@code refund_status} 与 Mock/支付宝 {@code result_code} 共用口径）。 */
    static final String STATE_SUCCESS = "SUCCESS";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int TAG_LENGTH_BIT = 128;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WechatCallbackDecoder() {
    }

    /**
     * 真实通道：解析外层报文 → AES-GCM 解密 {@code resource} → 提取强类型结果。
     *
     * @param apiV3Key 微信 APIv3 密钥（32 字节 UTF-8）
     * @param rawBody  回调原始报文（验签用的同一份字节序列）
     * @return 强类型解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 报文非法 / resource 缺字段 / 解密失败 / 缺 out_trade_no
     */
    static WechatCallbackResult decode(String apiV3Key, String rawBody) {
        return extract(decryptOuterResource(apiV3Key, rawBody));
    }

    /**
     * 真实通道（退款回调）：解析外层报文 → AES-GCM 解密 {@code resource} → 提取退款强类型结果。
     *
     * <p>与 {@link #decode} 共用同一套「外层结构解析 + AES-GCM 解密」，仅在明文字段提取上分道
     * （退款通知的字段是 {@code out_refund_no} / {@code refund_id} / {@code refund_status}）。
     *
     * @param apiV3Key APIv3 密钥（32 字节 UTF-8）
     * @param rawBody  回调原始报文
     * @return 强类型退款解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 报文非法 / resource 缺字段 / 解密失败 / 缺 out_refund_no
     */
    static WechatRefundCallbackResult decodeRefund(String apiV3Key, String rawBody) {
        return extractRefund(decryptOuterResource(apiV3Key, rawBody));
    }

    /**
     * 解析外层报文并解密 {@code resource}，返回明文 JSON —— 支付回调与退款回调的公共前置。
     *
     * @param apiV3Key APIv3 密钥
     * @param rawBody  回调原始报文
     * @return 解密后的明文 JSON
     * @throws BizException {@code ILLEGAL_REQUEST} 报文非法 / resource 缺字段 / 解密失败
     */
    private static String decryptOuterResource(String apiV3Key, String rawBody) {
        JsonNode outer = readTree(rawBody, "微信回调报文不是合法 JSON");
        JsonNode resource = outer.path(FIELD_RESOURCE);
        if (resource.isMissingNode() || resource.isNull()) {
            throw illegal("微信回调报文缺少 resource 节点");
        }
        String ciphertext = text(resource, FIELD_CIPHERTEXT);
        String nonce = text(resource, FIELD_NONCE);
        if (ciphertext == null || nonce == null) {
            throw illegal("微信回调 resource 缺少 ciphertext/nonce");
        }
        String associatedData = resource.path(FIELD_ASSOCIATED_DATA).asText("");
        return decryptResource(apiV3Key, ciphertext, nonce, associatedData);
    }

    /**
     * 按微信 v3 规范解密 {@code resource} 密文（独立方法，便于用固定密钥做全链路固件测试）。
     *
     * @param apiV3Key       APIv3 密钥（UTF-8 32 字节）
     * @param ciphertext     Base64(密文 ‖ 认证标签)
     * @param nonce          IV（UTF-8 12 字节）
     * @param associatedData AAD（可为空串）
     * @return 解密后的明文 JSON
     * @throws BizException {@code ILLEGAL_REQUEST} 解密失败（密钥/IV/AAD/密文任一不匹配）
     */
    static String decryptResource(String apiV3Key, String ciphertext, String nonce, String associatedData) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_LENGTH_BIT, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null && !associatedData.isEmpty()) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 密钥/IV/AAD 不匹配、Base64 非法、标签校验失败均落此分支；细节只进日志，不外传
            log.error("微信回调解密失败: nonceLength={}, aadLength={}",
                    nonce == null ? 0 : nonce.length(), associatedData == null ? 0 : associatedData.length(), e);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "微信回调解密失败");
        }
    }

    /**
     * 从<b>明文</b>回调 JSON 提取强类型结果 —— 真实通道（解密后）与 Mock 通道共用同一提取口径。
     * 成功判定：{@code trade_state} 优先，缺省回落 {@code result_code}，取值为 {@code SUCCESS} 即成功。
     *
     * @param plainJson 明文 JSON（微信解密产物 / Mock 直接投喂的报文）
     * @return 强类型解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 非 JSON / 缺 {@code out_trade_no}
     */
    static WechatCallbackResult extract(String plainJson) {
        JsonNode node = readTree(plainJson, "回调报文不是合法 JSON");
        String outTradeNo = text(node, FIELD_OUT_TRADE_NO);
        if (outTradeNo == null || outTradeNo.isBlank()) {
            throw illegal("回调报文缺少 out_trade_no");
        }
        String rawState = text(node, FIELD_TRADE_STATE);
        if (rawState == null) {
            rawState = text(node, FIELD_RESULT_CODE);
        }
        return new WechatCallbackResult(outTradeNo, STATE_SUCCESS.equals(rawState),
                text(node, FIELD_TRANSACTION_ID), rawState);
    }

    /**
     * 从<b>明文</b>退款回调 JSON 提取强类型结果 —— 真实通道（解密后）与 Mock 通道共用同一提取口径。
     * 成功判定：{@code refund_status} 取值为 {@code SUCCESS} 即成功。
     *
     * @param plainJson 明文 JSON（微信解密产物 / Mock 直接投喂的报文）
     * @return 强类型退款解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 非 JSON / 缺 {@code out_refund_no}
     */
    static WechatRefundCallbackResult extractRefund(String plainJson) {
        JsonNode node = readTree(plainJson, "退款回调报文不是合法 JSON");
        String outRefundNo = text(node, FIELD_OUT_REFUND_NO);
        if (outRefundNo == null || outRefundNo.isBlank()) {
            throw illegal("退款回调报文缺少 out_refund_no");
        }
        String rawStatus = text(node, FIELD_REFUND_STATUS);
        return new WechatRefundCallbackResult(outRefundNo, STATE_SUCCESS.equals(rawStatus),
                text(node, FIELD_REFUND_ID), rawStatus);
    }

    private static JsonNode readTree(String json, String errorMessage) {
        if (json == null || json.isBlank()) {
            throw illegal(errorMessage);
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.error("回调报文 JSON 解析失败: {}", errorMessage, e);
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, errorMessage);
        }
    }

    /** 取文本字段：节点缺失/非文本时返回 {@code null}，空串归一为 {@code null}。 */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static BizException illegal(String message) {
        return new BizException(ErrorCode.ILLEGAL_REQUEST, message);
    }
}
