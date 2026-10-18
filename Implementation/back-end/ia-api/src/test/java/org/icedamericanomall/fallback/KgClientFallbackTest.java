package org.icedamericanomall.fallback;

import feign.Request;
import feign.Response;
import org.icedamericanomall.client.KgClient;
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
 * KG 清边降级语义测试（U17，2026-10-18）—— <b>{@code deleteEdges} 改抛，其余方法仍 fail-open</b>。
 *
 * <p><b>为什么需要这个文件</b>：改抛之前，{@code KgClientFallback} 没有任何专属测试
 * （全仓 grep 得到 0 个 {@code KgClientFallbackTest}），它的行为只靠
 * {@code FallbackDegradeContractTest} 的发现式扫描间接覆盖。而扫描只能证明「当前与处置表一致」，
 * 说不清「为什么这个方法必须抛」—— 那部分必须由类注释 + 本文件承担。
 *
 * <p><b>为什么只改这一个方法</b>：同一个工厂里 {@code edgeUpsert} 走哨兵 {@code -1}、
 * {@code neighbors} 返回空列表，二者按 PRD §16.8.1「全链路 fail-open」是<b>有意</b>不抛的
 * （{@code KgPairingManager} 没有 try/catch，抛错会打断商品详情）。所以工厂级仍是
 * {@code RETURNS}，只有 {@code deleteEdges} 在 {@code METHOD_DISPOSITIONS} 里覆盖为 {@code THROWS} ——
 * 「一个方法一个结论，别把工厂级当成整类」。
 *
 * <p>断言<b>最具体类型</b>：4xx → {@link UpstreamException}（透传）；其余 → {@link BizException}
 * （{@link ErrorCode#SYSTEM_BUSY}）。不用 {@code Exception.class} —— 那对「抛什么都行」都成立。
 */
@DisplayName("KgClientFallback — deleteEdges 改抛（4xx 透传 / 其余 SYSTEM_BUSY），edgeUpsert 与 neighbors 维持 fail-open")
class KgClientFallbackTest {

    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"节点不存在\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"t-1\"}";

    private static final String TYPED_503 = "{\"code\":1001,\"msg\":\"系统繁忙\","
            + "\"category\":\"TRANSIENT\",\"retryable\":true,\"traceId\":\"t-2\"}";

    private static final String SRC_TYPE = "spu";
    private static final long SRC_ID = 1L;

    @Test
    @DisplayName("清边 + 下游 4xx（CLIENT）→ 抛 UpstreamException，业务码与状态码原样")
    void shouldThrowUpstreamException_whenDeleteEdgesRejectedAsClientError() {
        KgClient client = new KgClientFallback().create(decode(404, TYPED_404));

        UpstreamException thrown = assertThrows(UpstreamException.class,
                () -> client.deleteEdges(SRC_TYPE, SRC_ID));

        assertThat(thrown.getCode()).isEqualTo(1103);
        assertThat(thrown.getUpstreamStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("清边 + 下游不可用（TRANSIENT）→ 抛 BizException(SYSTEM_BUSY)，不得静默空操作")
    void shouldThrowSystemBusy_whenDeleteEdgesUnavailable() {
        KgClient client = new KgClientFallback().create(decode(503, TYPED_503));

        BizException thrown = assertThrows(BizException.class,
                () -> client.deleteEdges(SRC_TYPE, SRC_ID));

        assertThat(thrown.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_BUSY);
    }

    @Test
    @DisplayName("★ 反向对照 — neighbors 仍返回空列表（不得被顺手改成抛；那会打断商品详情）")
    void shouldStillReturnEmptyList_whenNeighborsUnavailable() {
        KgClient client = new KgClientFallback().create(decode(503, TYPED_503));

        assertThat(client.neighbors(SRC_TYPE, SRC_ID, null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("★ 反向对照 — edgeUpsert 仍返回哨兵 -1（同理，不得被顺手改成抛）")
    void shouldStillReturnSentinel_whenEdgeUpsertUnavailable() {
        KgClient client = new KgClientFallback().create(decode(503, TYPED_503));

        assertThat(client.edgeUpsert(null)).isEqualTo(KgClientFallback.UPSERT_FALLBACK_SENTINEL);
    }

    /** 用真实解码器造一枚上游异常。 */
    private static UpstreamException decode(int status, String body) {
        Response response = Response.builder()
                .status(status)
                .reason("r" + status)
                .headers(Map.of())
                .request(Request.create(Request.HttpMethod.DELETE,
                        "http://downstream/internal/kg/edges", Map.of(), (byte[]) null,
                        StandardCharsets.UTF_8, null))
                .body(body, StandardCharsets.UTF_8)
                .build();
        return (UpstreamException) new UpstreamErrorDecoder().decode("KgClient#deleteEdges(String,Long)", response);
    }
}
