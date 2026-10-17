package org.icedamericanomall.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErrorCategory} 派生规则 —— 锁死 {@code collaboration/09-constraints.md} §三·八 的
 * 「四类划分」与「{@code retryable = (category == TRANSIENT)}」。
 *
 * <p>本测试刻意做成<b>全量遍历</b>（99+ 个 {@code ErrorCode} 逐个判定）而不是抽查几个：
 * 派生函数的价值恰恰在于「新增错误码时自动归类」，抽查证明不了这一点。若将来有人把派生改回
 * 「逐个手填」或漏了某个枚举，这里会第一时间红。
 */
@DisplayName("ErrorCategory — 状态码派生 / 覆盖表 / retryable 派生")
class ErrorCategoryTest {

    /**
     * 覆盖表<b>预期成员</b>（测试侧独立声明，不从实现里读 —— 否则实现改错时测试跟着一起错）。
     *
     * <p>这三个是「HTTP 500 但责任方在外部第三方」的例外，理由见
     * {@link ErrorCategory#STATUS_AMBIGUOUS_OVERRIDES} 的逐条注释。
     */
    private static final Set<ErrorCode> EXPECTED_OVERRIDES = EnumSet.of(
            ErrorCode.THIRD_PARTY_RESPONSE_PARSE_ERROR,
            ErrorCode.THIRD_PARTY_UNKNOWN_ERROR,
            ErrorCode.THIRD_PARTY_UNDEFINED);

    // ==================== 四类划分：逐条按用户裁决的判据 ====================

    @Test
    @DisplayName("CLIENT — 4xx（400/401/403/404/405/409/422）一律 CLIENT，且不可重试")
    void shouldMapAllClientErrorsToClient() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED,
                HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_ENTITY)) {
            assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(status), status + " 应归 CLIENT");
            assertEquals(ErrorCategory.CLIENT, ErrorCategory.ofStatus(status.value()));
            assertFalse(ErrorCategory.CLIENT.isRetryable(), "重试客户错误等于放大故障");
        }
    }

    @Test
    @DisplayName("RULE — 200 + 业务 code（业务规则拒绝的既有形态）")
    void shouldMapHttpOkToRule() {
        assertSame(ErrorCategory.RULE, ErrorCategory.of(HttpStatus.OK));
        assertSame(ErrorCategory.RULE, ErrorCategory.ofStatus(200));
        assertFalse(ErrorCategory.RULE.isRetryable());
        // 代表项：库存不足（3012/200）—— 资源存在、请求合法，只是规则不允许
        assertSame(ErrorCategory.RULE, ErrorCategory.of(ErrorCode.STOCK_INSUFFICIENT));
        assertSame(ErrorCategory.RULE, ErrorCategory.of(ErrorCode.CART_ITEM_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("TRANSIENT — 429 / 502 / 503 / 504 / 408，且可重试")
    void shouldMapTransientStatusesToTransient() {
        for (HttpStatus status : List.of(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.BAD_GATEWAY,
                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT,
                HttpStatus.REQUEST_TIMEOUT)) {
            assertEquals(ErrorCategory.TRANSIENT, ErrorCategory.of(status), status + " 应归 TRANSIENT");
            assertTrue(ErrorCategory.TRANSIENT.isRetryable());
        }
        // 408 落在 4xx 区间但「不是调用方语义错误」—— 显式例外，不得被「4xx ⇒ CLIENT」吞掉
        assertSame(ErrorCategory.CLIENT, ErrorCategory.ofStatus(409));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.ofStatus(408));
    }

    @Test
    @DisplayName("BUG — 500 且非基础设施（NPE / 契约违约 / 加解密配置）→ 不重试")
    void shouldMapNonInfrastructure500ToBug() {
        assertSame(ErrorCategory.BUG, ErrorCategory.of(HttpStatus.INTERNAL_SERVER_ERROR));
        assertSame(ErrorCategory.BUG, ErrorCategory.of(ErrorCode.INTERNAL_ERROR));
        // 同为「第三方」前缀但状态码是 503 的项（如 THIRD_PARTY_ERROR）走状态码派生 → TRANSIENT，
        // 只有「500 的第三方故障」才需要覆盖表。这一行锁死两条路径不互相污染。
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(ErrorCode.THIRD_PARTY_ERROR));
        // 加解密错误是确定性失败（密钥配错）：重试必然重复失败，正该归 BUG 并告警
        assertSame(ErrorCategory.BUG, ErrorCategory.of(ErrorCode.ENCRYPT_ERROR));
        assertSame(ErrorCategory.BUG, ErrorCategory.of(ErrorCode.DECRYPT_ERROR));
        assertSame(ErrorCategory.BUG, ErrorCategory.of(ErrorCode.HASH_ERROR));
        assertFalse(ErrorCategory.BUG.isRetryable());
    }

    // ==================== 覆盖表：只收「状态码语义不足」的例外 ====================

    @Test
    @DisplayName("覆盖表 — 第三方 500 类错误改判 TRANSIENT（状态码无法表达「责任在外部」）")
    void shouldOverrideThirdParty500ToTransient() {
        for (ErrorCode code : EXPECTED_OVERRIDES) {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, code.getHttpStatus(),
                    "覆盖表项必须真的是 500（否则不需要覆盖）");
            // 关键：单看状态码会判成 BUG —— 这正是覆盖表存在的理由
            assertSame(ErrorCategory.BUG, ErrorCategory.ofStatus(code.getHttpStatus().value()),
                    "按状态码派生会误判成 BUG");
            assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(code),
                    "第三方故障应归 TRANSIENT，可重试");
            assertTrue(ErrorCategory.of(code).isRetryable());
        }
    }

    @Test
    @DisplayName("覆盖表 — 成员集合与实现完全一致（多一条/少一条都失败，防止「第二套映射」蔓延）")
    void shouldKeepOverrideTableExactlyAsDeclared() {
        Set<ErrorCode> actual = new LinkedHashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (ErrorCategory.of(code) != ErrorCategory.ofStatus(code.getHttpStatus().value())) {
                actual.add(code);
            }
        }
        assertEquals(EXPECTED_OVERRIDES.stream().map(Enum::name).toList(),
                new ArrayList<>(actual).stream().map(Enum::name).toList(),
                "覆盖表只应有这些成员；若确需新增，必须在实现里逐条写明「为何不能靠状态码判定」并同步本测试");
    }

    // ==================== retryable 派生规则 ====================

    @Test
    @DisplayName("retryable — 全量 ErrorCode 恒等于 (category == TRANSIENT)，CLIENT/RULE/BUG 一律 false")
    void shouldDeriveRetryableFromCategory() {
        for (ErrorCode code : ErrorCode.values()) {
            ErrorCategory category = ErrorCategory.of(code);
            assertEquals(category == ErrorCategory.TRANSIENT, category.isRetryable(),
                    code + " 的 retryable 必须由 category 派生");
        }
    }

    @Test
    @DisplayName("全量归类 — 每个 ErrorCode 都有类别（新增枚举不得落空）")
    void shouldCategorizeEveryErrorCode() {
        List<String> uncategorized = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (ErrorCategory.of(code) == null) {
                uncategorized.add(code.name());
            }
        }
        assertTrue(uncategorized.isEmpty(), "未归类的错误码: " + uncategorized);
        assertTrue(ErrorCode.values().length >= 99, "ErrorCode 数量基线为 99+，实际 " + ErrorCode.values().length);
    }

    @Test
    @DisplayName("未知 —— null 错误码/状态码一律 TRANSIENT（未知按可重试处理，绝不当成功）")
    void shouldTreatUnknownAsTransient() {
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of((ErrorCode) null));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of((HttpStatus) null));
        // 0 / 1xx / 3xx 不在任何语义区间内 → 未知 → 可重试
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.ofStatus(0));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.ofStatus(302));
    }

    @Test
    @DisplayName("代表项回归 — 1103(404) 是 CLIENT；1014(429)/3100(503)/3101(504) 是 TRANSIENT")
    void shouldCategorizeRepresentativeCodes() {
        // ★ 批次 ⑤ 的核心用例：商品不存在的 404 必须判 CLIENT（透传），不得改写为 SYSTEM_BUSY
        assertSame(ErrorCategory.CLIENT, ErrorCategory.of(ErrorCode.NO_RESOURCE_FOUND));
        assertFalse(ErrorCategory.of(ErrorCode.NO_RESOURCE_FOUND).isRetryable());
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(ErrorCode.FREQUENT_ERROR));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(ErrorCode.SYSTEM_BUSY));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(ErrorCode.AI_SERVICE_UNAVAILABLE));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(ErrorCode.AI_TIMEOUT));
        // 登录失败 401 → CLIENT（凭证类，调用方不该重试）
        assertSame(ErrorCategory.CLIENT, ErrorCategory.of(ErrorCode.PASSWORD_ERROR));
        assertNotNull(ErrorCategory.of(ErrorCode.BUSINESS_EXECUTION_EXCEPTION));
    }

    // ==================== ② 异常类型优先（批次 ⑤b）====================

    /**
     * ★ 批次 ⑤b 的核心用例：熔断/连接失败/超时/池耗尽在 Feign 侧<b>没有 HTTP 响应</b>，
     * 它们只以异常形式出现。
     *
     * <p><b>锁死的缺陷（修复前会失败）</b>：判据只看状态码，这类调用要么没有状态码可看
     * （落到「未知」），要么被兜底成 500 → {@code BUG} —— 而 {@code BUG} 不可重试，
     * 等于「上游抖一下就永久失败 + 误报代码缺陷」。本用例逐个类型钉死 {@code TRANSIENT}。
     */
    @Test
    @DisplayName("异常类型优先 — 连接失败/超时/池耗尽（无 HTTP 响应）→ TRANSIENT 且可重试")
    void shouldMapNetworkExceptionsToTransient() {
        List<Throwable> causes = List.of(
                new java.net.ConnectException("Connection refused"),
                new java.net.SocketTimeoutException("Read timed out"),
                new java.io.IOException("Connection reset by peer"),
                new java.util.concurrent.TimeoutException("timed out"),
                new java.util.concurrent.RejectedExecutionException("pool exhausted"));

        for (Throwable cause : causes) {
            assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(cause),
                    cause.getClass().getSimpleName() + " 应归 TRANSIENT（没有响应可看，只能按类型判）");
            // 即便「状态码」这一侧给的是 500，异常类型也必须赢
            assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(500, cause),
                    cause.getClass().getSimpleName() + " 必须优先于 500 派生出的 BUG");
            assertTrue(ErrorCategory.of(cause).isRetryable(), "暂时性故障必须可重试");
            assertTrue(ErrorCategory.isTransientCause(cause));
        }
    }

    @Test
    @DisplayName("异常类型 — 命中按父类链（IOException 的子类同样算网络类故障）")
    void shouldMatchTransientTypeAlongSuperclassChain() {
        assertTrue(ErrorCategory.isTransientCause(new java.io.FileNotFoundException("no such file")),
                "FileNotFoundException 的父类链含 java.io.IOException");
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(new java.io.FileNotFoundException("x")));
    }

    @Test
    @DisplayName("异常类型 — 递归解 cause 链（装饰器/重试/熔断会层层包装，不能只看第一层）")
    void shouldUnwrapCauseChain() {
        Throwable decorated = new RuntimeException("circuit-breaker decorator",
                new RuntimeException("retry wrapper",
                        new java.net.ConnectException("Connection refused")));

        assertTrue(ErrorCategory.isTransientCause(decorated), "真正的故障类型在第三层");
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(500, decorated));
    }

    /**
     * 自引用异常：{@code Throwable#initCause(this)} 被 JDK 拒绝（"Self-causation not permitted"），
     * 真实的自引用链只会出现在「重写 {@code getCause()} 的装饰器异常」上 —— 这里用一个最小的
     * 同类实现复现该畸形链，验证分类器<b>有界</b>。
     */
    private static final class SelfCausingException extends RuntimeException {
        private SelfCausingException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    @Test
    @DisplayName("异常链防环 — 自引用 / 互相引用的畸形链必须终止（不得在降级路径上死循环）")
    void shouldNotLoopOnCyclicCauseChain() {
        SelfCausingException selfReferencing = new SelfCausingException("self");

        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second); // first → second → first（环）

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertFalse(ErrorCategory.isTransientCause(selfReferencing), "自引用链上没有暂时性故障类型");
            assertFalse(ErrorCategory.isTransientCause(first), "环形链必须停下来，不能无限回溯");
        }, "异常链回溯必须有界（环 / 深度上限）");
        // 环上判不出线索 → 交给状态码派生，绝不因此误判成 TRANSIENT 之外的东西或抛异常
        assertSame(ErrorCategory.BUG, ErrorCategory.of(500, first));
    }

    /**
     * ★ 反向回归：<b>500 且无异常线索 → BUG 且不可重试</b>。
     *
     * <p>与上一条构成双向防线：既防「一律按状态码（漏掉熔断/超时）」，也防
     * 「一律 TRANSIENT（把真的代码缺陷说成暂时故障，无限重试且不告警）」。
     */
    @Test
    @DisplayName("500 且无异常线索 → BUG 且 retryable=false（防「一律 TRANSIENT」反向回归）")
    void shouldKeepPlainFailureAsBug() {
        assertSame(ErrorCategory.BUG, ErrorCategory.of(500, null));
        assertFalse(ErrorCategory.of(500, null).isRetryable());

        // 非网络类异常（NPE / 契约违约）同样不能因为「有个异常」就被当成暂时性故障
        assertSame(ErrorCategory.BUG, ErrorCategory.of(500, new NullPointerException("npe")));
        assertFalse(ErrorCategory.of(500, new IllegalStateException("契约违约")).isRetryable());
        assertFalse(ErrorCategory.isTransientCause(new NullPointerException("npe")));
    }

    @Test
    @DisplayName("未知 — 既无类型线索也无状态码 → TRANSIENT（未知按可重试处理，绝不当成功）")
    void shouldTreatUnknownCauseAsTransient() {
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of((Throwable) null));
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of((Integer) null, null));
        // 有异常但类型无结论、又没状态码：仍是「未知」→ 可重试
        assertSame(ErrorCategory.TRANSIENT, ErrorCategory.of(new NullPointerException("npe")));
    }

    // ==================== ③ 覆盖表启动期自检（批次 ⑤b）====================

    /**
     * ★ 批次 ⑤b：覆盖表配错必须<b>启动即失败</b>（{@code IllegalStateException}），不得静默失效。
     *
     * <p>锁死的缺陷（修复前会失败）：此前覆盖表只由 {@code buildOverrides()} 手工填写，
     * 没有自检 —— 一条「值 = 状态码派生结果」的无意义覆盖不会报错，只会让读者误以为
     * 「这里被特殊处理过」，而真实判据仍是状态码。生产上表现为「第三方 500 又被判 BUG」。
     */
    @Test
    @DisplayName("覆盖表自检 — 值与该码状态码派生结果相同 → IllegalStateException（该条覆盖无意义）")
    void shouldRejectOverrideEqualToDerivedCategory() {
        Map<ErrorCode, ErrorCategory> meaningless = new EnumMap<>(ErrorCode.class);
        // INTERNAL_ERROR 是 500 → 派生即 BUG，把它「覆盖」成 BUG 什么也没纠正
        meaningless.put(ErrorCode.INTERNAL_ERROR, ErrorCategory.BUG);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ErrorCategory.validateOverrides(meaningless),
                "无意义的覆盖项必须让启动失败，而不是静默保留");
        assertTrue(ex.getMessage().contains(ErrorCode.INTERNAL_ERROR.name()),
                "异常信息要点名问题条目，否则运维无法定位: " + ex.getMessage());
    }

    @Test
    @DisplayName("覆盖表自检 — null 键（不是真实 ErrorCode）→ IllegalStateException")
    void shouldRejectNullKeyOverride() {
        Map<ErrorCode, ErrorCategory> withNullKey = new HashMap<>();
        withNullKey.put(null, ErrorCategory.TRANSIENT);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ErrorCategory.validateOverrides(withNullKey));
        assertTrue(ex.getMessage().contains("null"), "异常信息要指出空键: " + ex.getMessage());
    }

    @Test
    @DisplayName("覆盖表自检 — 条目数超过上限 MAX_OVERRIDES → IllegalStateException（该改派生，不是继续加表）")
    void shouldRejectOversizedOverrideTable() {
        Map<ErrorCode, ErrorCategory> tooMany = new EnumMap<>(ErrorCode.class);
        // 全部是 500 的码：覆盖成 TRANSIENT 与派生 BUG 不同（即「有意义」），只触发条目数上限
        for (ErrorCode code : List.of(ErrorCode.INTERNAL_ERROR, ErrorCode.THIRD_PARTY_RESPONSE_PARSE_ERROR,
                ErrorCode.THIRD_PARTY_UNKNOWN_ERROR, ErrorCode.THIRD_PARTY_UNDEFINED,
                ErrorCode.ENCRYPT_ERROR, ErrorCode.DECRYPT_ERROR, ErrorCode.HASH_ERROR)) {
            tooMany.put(code, ErrorCategory.TRANSIENT);
        }
        assertTrue(tooMany.size() > ErrorCategory.MAX_OVERRIDES, "构造的条目数必须真的越界");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ErrorCategory.validateOverrides(tooMany));
        assertTrue(ex.getMessage().contains(String.valueOf(ErrorCategory.MAX_OVERRIDES)),
                "异常信息要给出上限值: " + ex.getMessage());
    }

    @Test
    @DisplayName("覆盖表自检 — 真实覆盖表通过自检（合法形态：500 的第三方故障覆盖成 TRANSIENT）")
    void shouldAcceptRealOverrideTableShape() {
        Map<ErrorCode, ErrorCategory> legal = new EnumMap<>(ErrorCode.class);
        for (ErrorCode code : EXPECTED_OVERRIDES) {
            legal.put(code, ErrorCategory.TRANSIENT);
        }
        assertDoesNotThrow(() -> ErrorCategory.validateOverrides(legal),
                "当前三条覆盖项必须合法（否则应用启动就会失败）");
        assertTrue(legal.size() <= ErrorCategory.MAX_OVERRIDES);
    }
}
