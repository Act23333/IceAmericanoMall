package org.icedamericanomall.common.enums;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
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
 * <b>它受启动期自检约束</b>（{@link #validateOverrides}）：键必须是真实 {@code ErrorCode}、
 * 值不得与该码状态码的派生结果相同、条目数不得超过 {@link #MAX_OVERRIDES} —— 违反即
 * {@code IllegalStateException} 让应用<b>启动失败</b>，而不是静默失效。
 *
 * <p><b>判定顺序（钉死，2026-10-18 批次 ⑤b）</b>：<b>异常类型 → HTTP 状态码 → 500 才是 BUG</b>。
 *
 * <p><b>为什么「异常类型」要排在状态码之前</b>：熔断打开、连接失败、读超时、线程池/信号量耗尽
 * 在 Feign 侧<b>根本不产生 HTTP 响应</b>（没有状态码可看），它们只以异常形式出现在降级路径上
 * （{@code RetryableException} / {@code ConnectException} / {@code CallNotPermittedException} …）。
 * 若只看状态码，这些调用会被兜底成「未知」甚至「BUG」——「BUG 不可重试」直接堵死重试，
 * 把一次上游抖动升级成一次值班事故。故先按 {@link #of(Throwable)} 判类型，命中即
 * {@link #TRANSIENT}；只有类型判不出结论时，才回落到状态码派生（{@link #ofStatus(int)}）；
 * 状态码 500 且无任何异常线索，才判 {@link #BUG}。
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
     * 覆盖表条目数<b>上限</b>（启动期强制，见 {@link #validateOverrides}）。
     *
     * <p>为什么要有上限：覆盖表是「状态码语义不足」的例外清单，<b>不是</b>第二套映射。
     * 一旦条目越攒越多，说明真正的信号是「派生逻辑不适用了，该改派生」——继续加表只会让
     * 「同一语义」分散到 99 个枚举项的逐条手填上，必然漂移。上限取 5（当前用 3 条）：
     * 留 2 格给「第三方故障」这类真实例外，越界即报错，逼着讨论派生规则本身。
     */
    static final int MAX_OVERRIDES = 5;

    /**
     * 视为「<b>暂时性故障</b>」的异常类型全名 —— 命中即 {@link #TRANSIENT}（可重试）。
     *
     * <p><b>输入口径</b>：这些是<b>没有 HTTP 响应</b>的故障形态（连接、超时、熔断、池耗尽）：
     * <table border="1">
     *   <caption>逐条判据</caption>
     *   <tr><th>类型</th><th>何时出现</th><th>为何是 TRANSIENT</th></tr>
     *   <tr><td>{@code feign.RetryableException}</td>
     *       <td>Feign 传输层失败（通常包装 ConnectException / SocketTimeoutException）</td>
     *       <td>对端没连上 / 没按时回 → 重试有机会成功</td></tr>
     *   <tr><td>{@code java.net.ConnectException}</td><td>TCP 连接被拒 / 不可达</td>
     *       <td>实例重启、扩缩容窗口 → 换一个实例即可</td></tr>
     *   <tr><td>{@code java.net.SocketTimeoutException}</td><td>读/连接超时</td>
     *       <td>对端慢但可能不坏 → 退避重试</td></tr>
     *   <tr><td>{@code java.io.IOException}</td><td><b>网络类</b> IO 故障（连接重置、流被关闭等）</td>
     *       <td>在 Feign 调用路径上 IO 异常只可能来自传输层</td></tr>
     *   <tr><td>{@code java.util.concurrent.TimeoutException}</td><td>异步等待超时（Resilience4j/CompletableFuture）</td>
     *       <td>同上：对端慢，非契约错误</td></tr>
     *   <tr><td>{@code java.util.concurrent.RejectedExecutionException}</td><td>线程池/执行器耗尽</td>
     *       <td>过载而非代码缺陷，退避后恢复</td></tr>
     *   <tr><td>{@code io.github.resilience4j.circuitbreaker.CallNotPermittedException}</td>
     *       <td><b>熔断打开</b></td><td>熔断本身就是「暂时别打」，恢复后自动放行</td></tr>
     *   <tr><td>{@code io.github.resilience4j.bulkhead.BulkheadFullException}</td>
     *       <td>隔舱（并发数）耗尽</td><td>过载 → 退避</td></tr>
     *   <tr><td>{@code com.alibaba.csp.sentinel.slots.block.BlockException}</td>
     *       <td>Sentinel 流控/降级阻断（trade-service 使用）</td><td>限流 → 退避</td></tr>
     * </table>
     *
     * <p><b>为什么按「类型全名」而不是 {@code instanceof}</b>：本类是 {@code ia-common}（共享内核），
     * 它<b>不声明</b> Feign / Resilience4j / Sentinel 依赖 —— 若为分类而引入这三个依赖，
     * 等于让「每个服务」都被迫携带全部容错库（gateway 用 Sentinel、其余用 Resilience4j、
     * 贸易域两者皆有）。按全名比对（沿<b>父类链</b>逐级比对，故子类自动命中）在缺失该类时
     * 自然不命中，<b>不引入编译期耦合</b>，也与 Spring 自身处理可选集成的做法一致。
     * 代价是「改名即失效」—— 因此本表是全仓唯一实现，且有回归测试直接构造<b>真实库类</b>来锁死：
     * {@code ErrorCategoryTest}（{@code ia-common}，JDK 网络类）与
     * {@code UpstreamFailuresTest}（{@code ia-api}，真实 {@code feign.RetryableException} 与
     * resilience4j {@code CallNotPermittedException}）。
     */
    private static final Set<String> TRANSIENT_TYPE_NAMES = Set.of(
            "feign.RetryableException",
            "java.net.ConnectException",
            "java.net.SocketTimeoutException",
            "java.io.IOException",
            "java.util.concurrent.TimeoutException",
            "java.util.concurrent.RejectedExecutionException",
            "io.github.resilience4j.circuitbreaker.CallNotPermittedException",
            "io.github.resilience4j.bulkhead.BulkheadFullException",
            "com.alibaba.csp.sentinel.slots.block.BlockException");

    /** 异常链回溯上限 —— 防畸形/自引用链把降级路径拖成死循环。 */
    private static final int MAX_CAUSE_DEPTH = 16;

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

    /**
     * <b>启动期自检</b> —— 覆盖表一旦配错就<b>启动失败</b>，绝不静默失效。
     *
     * <p>为什么必须是静态初始化块 + 抛异常，而不是「打个 warn 日志」：覆盖表的作用是
     * 「纠正状态码的误判」，若某条覆盖无效（值 = 派生结果）或不合法，运行时表现是
     * <b>静默回到错误判据</b>（如第三方 500 又被判成 BUG → 拦截重试 + 误告警），
     * 而这种偏差只在生产故障时才暴露。启动即失败是唯一能让问题在部署前被看见的方式。
     *
     * <p>校验内容见 {@link #validateOverrides(Map)}；位置必须在
     * {@link #STATUS_AMBIGUOUS_OVERRIDES} 声明<b>之后</b>（静态初始化按文本顺序执行）。
     */
    static {
        validateOverrides(STATUS_AMBIGUOUS_OVERRIDES);
    }

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

    // ==================== 异常类型优先（2026-10-18 批次 ⑤b） ====================

    /**
     * 按<b>异常类型</b>判定类别（异常类型优先判定）。
     *
     * <p>只回答「这个异常本身是不是暂时性故障」。命中 {@link #TRANSIENT_TYPE_NAMES}
     * 中任一类型（沿父类链、沿 {@code cause} 链递归解包、带防环）→ {@link #TRANSIENT}；
     * 否则按「未知」处理（{@link #TRANSIENT}，与 {@code UpstreamErrorDecoder} 的未知口径一致）。
     *
     * <p><b>注意分层</b>：本方法只认<b>类型</b>。若手上已有「已类型化的上游错误体」
     * （{@code ia-api} 的 {@code UpstreamException} 携带了下游类别），应先采信它 ——
     * 该路径由 {@code ia-api} 的 {@code UpstreamFailures#classify} 收口（{@code ia-common}
     * 不能反向依赖 {@code ia-api}）。
     *
     * @param cause 触发失败的异常（可为 {@code null}）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory of(Throwable cause) {
        return of(null, cause);
    }

    /**
     * <b>统一判定入口</b>：<b>异常类型 → HTTP 状态码 → 500 才是 BUG</b>。
     *
     * <p>顺序不可调换的理由与三类输入各自的作用见类注释。三条分支：
     * <ol>
     *   <li>{@link #isTransientCause(Throwable)} 命中 → {@link #TRANSIENT}
     *       （连接失败 / 超时 / 熔断打开 / 池耗尽：这些<b>没有 HTTP 响应</b>，看状态码永远看不出来）；</li>
     *   <li>否则有状态码线索 → {@link #ofStatus(int)} 派生（408/429/5xx-infra → TRANSIENT，
     *       4xx → CLIENT，2xx → RULE，<b>500 才是 BUG</b>）；</li>
     *   <li>两者都没有（既无类型线索也无状态码）→ {@link #TRANSIENT}：未知按可重试处理，
     *       <b>绝不当成功</b>，也不冒充「代码缺陷」制造误告警。</li>
     * </ol>
     *
     * @param httpStatus 下游 HTTP 状态码；无响应（连接层失败 / 熔断 / 超时）时传 {@code null}
     * @param cause      触发失败的异常（可为 {@code null}）
     * @return 处置类别，永不为 {@code null}
     */
    public static ErrorCategory of(Integer httpStatus, Throwable cause) {
        if (isTransientCause(cause)) {
            return TRANSIENT;
        }
        return httpStatus == null ? TRANSIENT : ofStatus(httpStatus);
    }

    /**
     * 异常链中是否出现「暂时性故障」类型的异常。
     *
     * <p><b>必须递归解 cause 链</b>：Feign / Resilience4j / 装饰器（重试、隔舱、熔断）会层层
     * 包装，只看第一层会漏掉真正的故障类型（{@code RuntimeException("decorator", CallNotPermittedException)}
     * 是常态而非例外）。同时<b>防环</b>：用身份集合去重 + 深度上限，自引用或互相引用的畸形链
     * 直接停下（返回 {@code false}，交给状态码派生），绝不在降级路径上死循环。
     *
     * @param cause 任意异常（可为 {@code null}）
     * @return {@code true} 表示链上出现连接/超时/熔断/池耗尽类异常
     */
    public static boolean isTransientCause(Throwable cause) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (!visited.add(current)) {
                return false; // 环：链已回到访问过的节点，停止（不得死循环）
            }
            if (matchesTransientType(current.getClass())) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                return false; // 自引用
            }
            current = next;
        }
        return false;
    }

    /**
     * 覆盖表自检（启动期由静态初始化块调用）。
     *
     * <p>三条约束，任一违反即 {@link IllegalStateException}：
     * <ol>
     *   <li><b>键必须是真实存在的 {@code ErrorCode}</b>（映射键类型已保证枚举性，此处挡住
     *       {@code null} 键 —— 反射/手工构造的表最容易引入这种「永远匹配不上」的条目）；</li>
     *   <li><b>值不得与该码 {@code HttpStatus} 的派生结果相同</b> —— 相同意味着这条覆盖
     *       <b>什么也没纠正</b>：它只会让读者误以为「这里有特殊处理」，实际判据仍是状态码
     *       （典型的配置错误：加了表却解决了别的问题）；</li>
     *   <li><b>条目数不得超过 {@link #MAX_OVERRIDES}</b> —— 超限说明派生逻辑该改，
     *       而不是继续加表。</li>
     * </ol>
     *
     * <p>刻意<b>不做</b>的事：不查「键是否属于某个白名单包」、不校验值与 {@code retryable} 的一致性
     * （后者由 {@link #isRetryable()} 单向派生，不存在不一致的可能）。
     *
     * @param overrides 待校验的覆盖表
     * @throws IllegalStateException 任一约束不成立
     */
    static void validateOverrides(Map<ErrorCode, ErrorCategory> overrides) {
        if (overrides == null) {
            throw new IllegalStateException("ErrorCategory 覆盖表为空(null)：启动自检失败");
        }
        if (overrides.size() > MAX_OVERRIDES) {
            throw new IllegalStateException("ErrorCategory 覆盖表条目数 " + overrides.size()
                    + " 超过上限 " + MAX_OVERRIDES
                    + "：条目越多说明状态码派生逻辑越不适用，应改派生，而不是继续加表");
        }
        for (Map.Entry<ErrorCode, ErrorCategory> entry : overrides.entrySet()) {
            ErrorCode code = entry.getKey();
            ErrorCategory overridden = entry.getValue();
            if (code == null) {
                throw new IllegalStateException("ErrorCategory 覆盖表存在 null 键：键必须是真实存在的 ErrorCode");
            }
            if (overridden == null) {
                throw new IllegalStateException("ErrorCategory 覆盖表 " + code.name() + " 未给出类别");
            }
            ErrorCategory derived = deriveByStatus(code);
            if (overridden == derived) {
                throw new IllegalStateException("ErrorCategory 覆盖表 " + code.name() + "=" + overridden.name()
                        + " 与该码 HTTP 状态码的派生结果相同 —— 该条覆盖无意义（配置错误），"
                        + "请删除它或修正取值");
            }
        }
    }

    /** 只按状态码派生（<b>不查覆盖表</b>）—— 供启动自检使用，避免自引用。 */
    private static ErrorCategory deriveByStatus(ErrorCode code) {
        HttpStatus status = code.getHttpStatus();
        return status == null ? TRANSIENT : ofStatus(status.value());
    }

    /** 沿 {@code type} 的父类链比对 {@link #TRANSIENT_TYPE_NAMES}（子类自动命中父类型名）。 */
    private static boolean matchesTransientType(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (TRANSIENT_TYPE_NAMES.contains(current.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建覆盖表（保持极短：只收「状态码相同但责任方不同」的错误码）。
     *
     * <p><b>返回值会被启动期自检</b>（静态初始化块 → {@link #validateOverrides(Map)}）：
     * 值与该码状态码派生结果相同的条目、{@code null} 键、条目数超过 {@link #MAX_OVERRIDES}
     * 都会让应用<b>启动失败</b>。新增条目时不必在这里手写校验，但必须能回答
     * 「{@link ErrorCode#getHttpStatus()} 为什么不足以判定」。
     */
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
