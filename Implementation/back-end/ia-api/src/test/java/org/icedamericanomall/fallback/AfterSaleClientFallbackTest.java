package org.icedamericanomall.fallback;

import feign.Request;
import feign.Response;
import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.exception.UpstreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 售后回写降级语义测试（U17，2026-10-18）—— <b>4xx 透传 / 其余 fail-closed（抛 {@code SYSTEM_BUSY}）</b>。
 *
 * <p><b>判据变了一次，这里必须跟着变</b>：改前本类断言的是 {@code assertDoesNotThrow}（静默返回），
 * 而那正是「静默成功 = 操作丢了」的形态 —— trade 不可用时调用方仍会打出
 * 「售后单已回写 REFUNDED」的成功日志，售后单实际停在 {@code APPROVED(2)}。
 *
 * <p>现在断言<b>最具体的异常类型</b>：
 * <ul>
 *   <li>下游 4xx → 必须抛 {@link UpstreamException}（透传，业务码与状态码原样）；</li>
 *   <li>下游 5xx / 超时 / 熔断 → 必须抛 {@link BizException}（{@link ErrorCode#SYSTEM_BUSY}）。</li>
 * </ul>
 * <p><b>刻意不写 {@code assertThrows(Exception.class, ...)}</b>：那对「抛什么都行」都成立，
 * 等于没断言 —— 「断言最具体类型」这条要求的由来就是这类假绿。
 */
@DisplayName("AfterSaleClientFallback — 4xx 透传 / 其余抛 SYSTEM_BUSY（不再静默返回）")
class AfterSaleClientFallbackTest {

    /** 下游 4xx + 已类型化错误体。 */
    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"订单不存在\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"t-1\"}";

    /** 下游 503 + 已类型化错误体。 */
    private static final String TYPED_503 = "{\"code\":1001,\"msg\":\"系统繁忙\","
            + "\"category\":\"TRANSIENT\",\"retryable\":true,\"traceId\":\"t-2\"}";

    @Test
    @DisplayName("下游 4xx（CLIENT）→ 抛 UpstreamException，业务码 1103 与状态码 404 原样透传")
    void shouldThrowUpstreamException_whenDownstreamRejectsAsClientError() {
        AfterSaleClient client = new AfterSaleClientFallback().create(decode(404, TYPED_404));

        UpstreamException thrown = assertThrows(UpstreamException.class, () -> client.markRefunded(1L));

        assertThat(thrown.getCode()).isEqualTo(1103);
        assertThat(thrown.getUpstreamStatus()).isEqualTo(404);
        assertThat(thrown.getMsg()).isEqualTo("订单不存在");
    }

    @Test
    @DisplayName("下游不可用（TRANSIENT）→ 抛 BizException(SYSTEM_BUSY)，绝不再静默返回")
    void shouldThrowSystemBusy_whenDownstreamUnavailable() {
        AfterSaleClient client = new AfterSaleClientFallback().create(decode(503, TYPED_503));

        BizException thrown = assertThrows(BizException.class, () -> client.markRefunded(1L));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_BUSY);
    }

    /** 用真实解码器造一枚上游异常（不手搓 ErrorInfo，避免「各喂一份自编数据」的接缝失明）。 */
    private static UpstreamException decode(int status, String body) {
        Response response = Response.builder()
                .status(status)
                .reason("r" + status)
                .headers(Map.of())
                .request(Request.create(Request.HttpMethod.PUT,
                        "http://downstream/internal/after-sale/probe", Map.of(), (byte[]) null,
                        StandardCharsets.UTF_8, null))
                .body(body, StandardCharsets.UTF_8)
                .build();
        return (UpstreamException) new UpstreamErrorDecoder().decode("AfterSaleClient#markRefunded(Long)", response);
    }
}
