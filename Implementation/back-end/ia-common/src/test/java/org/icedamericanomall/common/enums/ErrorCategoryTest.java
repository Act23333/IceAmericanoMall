package org.icedamericanomall.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
}
