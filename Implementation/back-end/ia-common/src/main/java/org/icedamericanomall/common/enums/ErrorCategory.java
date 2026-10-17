package org.icedamericanomall.common.enums;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 错误<b>处置类别</b> —— 跨服务错误传递的「调用方该怎么做」口径。
 *
 * <p><b>为什么需要它</b>：{@code collaboration/09-constraints.md} §三·八·1 要求
 * 「下游 4xx + 已知 code 必须原样透传，只有 5xx / 超时 / 熔断才允许改写成本域
 * {@code SYSTEM_BUSY}」，§三·八·3 要求「错误体必须足以判定处置（{@code code} +
 * {@code category} + {@code retryable} + {@code traceId}），调用方<b>不得</b>仅凭状态码
 * 猜测语义」。{@code code}（业务码）回答「是什么错」，{@link ErrorCategory} 回答
 * 「谁该负责、要不要重试」—— 后者是调用方唯一需要的处置决策，且<b>必须由状态码派生</b>，
 * 不能指望 99 个 {@link ErrorCode} 逐个手填（新增错误码时会漏填，且两处真相必然漂移）。
 *
 * <p><b>派生规则（本类是全仓唯一实现，禁止各服务自建映射）</b>：
 * <table border="1">
 *   <caption>category 判据与调用方动作</caption>
 *   <tr><th>category</th><th>判据</th><th>调用方动作</th></tr>
 *   <tr>
 *     <td>{@link #CLIENT}</td>
 *     <td><b>4xx</b>（400 / 401 / 403 / 404 / 405 / 409 / 422 …）—— 调用方的错：
 *         参数、资源不存在、凭证、越权、冲突</td>
 *     <td><b>原样透传</b>（不得改写成 {@code SYSTEM_BUSY}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #RULE}</td>
 *     <td><b>200 + 业务 code</b> —— 业务规则拒绝的既有形态（资源存在、请求合法，规则不允许）</td>
 *     <td>透传本域等价码 / 文案</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #TRANSIENT}</td>
 *     <td><b>408 / 429 / 502 / 503 / 504</b>，以及 500 中的<b>基础设施类</b>
 *         （见 {@link #STATUS_AMBIGUOUS_OVERRIDES}）；含超时、熔断</td>
 *     <td>可改写为本域 {@code SYSTEM_BUSY}，按业务决定 fail-open / fail-closed</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #BUG}</td>
 *     <td><b>500 且非基础设施</b>（NPE、契约违约、加解密配置错误…）—— 本服务的代码缺陷</td>
 *     <td>500 + 告警，<b>不泄露细节</b>；不重试（重试只会重复制造同一故障）</td>
 *   </tr>
 * </table>
 *
 * <p><b>retryable 派生规则（钉死）</b>：{@code retryable = (category == TRANSIENT)} ——
 * 见 {@link #isRetryable()}。{@code CLIENT} / {@code RULE} / {@code BUG} 一律
 * {@code false}：重试客户错误等于放大故障，重试代码缺陷只会重复失败。
 *
 * <p><b>为什么主体靠状态码派生</b>：{@code ErrorCode} 的 {@code httpStatus} 本身就是
 * 「四类划分」（{@code 09-constraints} §三·六）的落点 —— 4xx / 200 / 5xx 各有明确语义。
 * 派生函数只有一处，新增 {@code ErrorCode} 时自动归类，不需要单独维护 99 行映射。
 *
 * <p><b>覆盖表为何存在</b>：极少数错误码的 HTTP 状态码<b>语义不足</b>（同为 500，有的表示
 * 「我们代码有 bug」，有的表示「上游第三方出状况」）。这种情况只能按 {@code ErrorCode}
 * 逐条点名，且每一条都必须写清「为何不能靠状态码判定」—— 见
 * {@link #STATUS_AMBIGUOUS_OVERRIDES}。覆盖表刻意保持极短：它是例外，不是第二套映射。
 */
public enum ErrorCategory {

    /** 调用方的错（4xx）：参数 / 资源不存在 / 凭证 / 越权 / 冲突 → <b>原样透传</b>。 */
    CLIENT,

    /** 业务规则拒绝（200 + 业务 code）：透传本域等价码 / 文案。 */
    RULE,

    /** 暂时性故障（408 / 429 / 5xx 中的基础设施类 / 超时 / 熔断）→ <b>可重试</b>。 */
    TRANSIENT,

    /** 本服务的代码缺陷（500 且非基础设施）→ 500 + 告警，不泄露细节，不重试。 */
    BUG;

    /**
     * 视为「暂时性故障」的状态码集合 —— 这几个 4xx/5xx 与「谁写错了代码」无关。
     *
     * <p>逐个说明：
     * <ul>
     *   <li>{@code 408 Request Timeout}：对端没在约定时间内收完请求 → 重试即可，
     *       即便它落在 4xx 区间，也<b>不是</b>调用方的语义错误（这是与其它 4xx 的分界，
     *       因此必须显式列出，不能靠 「4xx ⇒ CLIENT」 一刀切）；</li>
     *   <li>{@code 429 Too Many Requests}：限流 → 退避后重试；</li>
     *   <li>{@code 502 Bad Gateway} / {@code 503 Service Unavailable} / {@code 504 Gateway Timeout}：
     *       网关或下游不可用 / 超时 → 重试或降级。</li>
     * </ul>
     */
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(
            HttpStatus.REQUEST_TIMEOUT.value(),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.BAD_GATEWAY.value(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.GATEWAY_TIMEOUT.value());

    /**
     * <b>状态码语义不足</b>的显式覆盖表（{@code ErrorCode} → 真实类别）。
     *
     * <p>规则：<b>只有</b>「HTTP 状态码相同、但责任方不同」的少数错误码才允许进表，
     * 且每条都要写清为何不能靠状态码判定。凡是能由 {@link ErrorCode#getHttpStatus()}
     * 唯一确定的，<b>禁止</b>入表（否则就是在维护第二套映射，与「派生」的初衷相悖）。
     *
     * <p>当前三条例外全部是「<b>第三方接口</b>故障」：它们由本服务的防腐层包装上报，
     * 因此 HTTP 上只能表现为 500（本服务确实没能完成这次调用），但责任方是<b>外部依赖</b>
     * ——归 {@link #BUG} 会误报「本服务代码有缺陷」并拦住重试，把一次上游抖动升级成一次
     * 值班事故。这类错误重试有机会成功，故归 {@link #TRANSIENT}。
     */
    private static final Map<ErrorCode, ErrorCategory> STATUS_AMBIGUOUS_OVERRIDES = buildOverrides();

    /** {@code retryable} 的唯一派生口径：只有暂时性故障可重试。 */
    public boolean isRetryable() {
        return this == TRANSIENT;
    }

    /**
     * 按错误码派生类别（<b>全仓唯一入口</b>）。
     *
     * <p>先查覆盖表（状态码语义不足的少数项），否则按 {@link ErrorCode#getHttpStatus()} 派生。
     *
     * @param errorCode 错误码；{@code null} 按「未知」处理（{@link #TRANSIENT}，与
     *                  {@code UpstreamErrorDecoder} 的未知降级口径一致 —— 未知一律按可重试处理，
     *                  <b>绝不</b>当成功）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory of(ErrorCode errorCode) {
        if (errorCode == null) {
            return TRANSIENT;
        }
        ErrorCategory overridden = STATUS_AMBIGUOUS_OVERRIDES.get(errorCode);
        if (overridden != null) {
            return overridden;
        }
        return of(errorCode.getHttpStatus());
    }

    /**
     * 按 HTTP 状态码派生类别。
     *
     * @param httpStatus HTTP 状态码；{@code null} 按「未知」处理（{@link #TRANSIENT}）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory of(HttpStatus httpStatus) {
        return httpStatus == null ? TRANSIENT : ofStatus(httpStatus.value());
    }

    /**
     * 按 HTTP 状态码数值派生类别（{@code UpstreamErrorDecoder} 解析下游响应时使用）。
     *
     * @param status HTTP 状态码数值
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory ofStatus(int status) {
        if (TRANSIENT_STATUSES.contains(status)) {
            return TRANSIENT;
        }
        if (status >= 500) {
            // 5xx 默认 BUG：能靠状态码区分出来的基础设施类已在 TRANSIENT_STATUSES 里；
            // 剩下的「500 基础设施类」必须走覆盖表（见 STATUS_AMBIGUOUS_OVERRIDES）。
            return BUG;
        }
        if (status >= 400) {
            return CLIENT;
        }
        if (status >= 200 && status < 300) {
            // 2xx：这里是「200 信封承载的业务规则拒绝」（内部接口的唯一 2xx 错误形态）。
            return RULE;
        }
        // 0 / 1xx / 3xx：不构成可判定的错误语义 —— 按「未知」处理，可重试，绝不当作成功。
        return TRANSIENT;
    }

    /** 构建覆盖表（保持极短：只收「状态码相同但责任方不同」的错误码）。 */
    private static Map<ErrorCode, ErrorCategory> buildOverrides() {
        EnumMap<ErrorCode, ErrorCategory> overrides = new EnumMap<>(ErrorCode.class);

        // ① 第三方响应解析失败（4007 / HTTP 500）—— 不能靠状态码判定的原因：
        //    缺陷不在本服务，而在上游第三方返回了违约报文（截断 / 乱码 / 缺字段 / 非 JSON）。
        //    本服务的防腐层只是「如实上报」，HTTP 层只能给 500。重试有机会拿到正常报文，
        //    归 BUG 会把它记成「本服务代码缺陷」并阻止调用方重试。
        overrides.put(ErrorCode.THIRD_PARTY_RESPONSE_PARSE_ERROR, TRANSIENT);

        // ② 第三方未知错误（4008 / HTTP 500）—— 同上：是外部依赖出了未知状况，
        //    不是本服务的代码缺陷；状态码 500 无法表达「责任在外部」。
        overrides.put(ErrorCode.THIRD_PARTY_UNKNOWN_ERROR, TRANSIENT);

        // ③ 未分类第三方错误兜底（4999 / HTTP 500）—— 语义本身就是「外部依赖的未知状况」，
        //    与 ② 同口径；同样无法用 500 区分责任方。
        overrides.put(ErrorCode.THIRD_PARTY_UNDEFINED, TRANSIENT);

        // 反例（刻意<b>不</b>入表）：ENCRYPT_ERROR / DECRYPT_ERROR / HASH_ERROR（5000-5002 / 500）
        // 与 INTERNAL_ERROR（1000 / 500）都是确定性失败 —— 密钥配置错误或本服务代码缺陷，
        // 重试必然重复失败，正该归 BUG 并告警；状态码已足以判定，无需覆盖。
        return Collections.unmodifiableMap(overrides);
    }
}
