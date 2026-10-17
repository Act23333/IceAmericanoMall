package org.icedamericanomall.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void shouldHaveCorrectCodeForEachError() {
        assertEquals(1000, ErrorCode.INTERNAL_ERROR.getCode());
        assertEquals(1011, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(1012, ErrorCode.TOKEN_EXPIRED.getCode());
        assertEquals(1013, ErrorCode.FORBIDDEN.getCode());
        assertEquals(1014, ErrorCode.FREQUENT_ERROR.getCode());
        assertEquals(1101, ErrorCode.PARAM_ERROR.getCode());
        assertEquals(1102, ErrorCode.USER_NOT_FOUND.getCode());
        assertEquals(3000, ErrorCode.BUSINESS_EXECUTION_EXCEPTION.getCode());
        assertEquals(3007, ErrorCode.PASSWORD_ERROR.getCode());
    }

    @Test
    void shouldMapToCorrectHttpStatus() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getHttpStatus());
        assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.getHttpStatus());
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.PARAM_ERROR.getHttpStatus());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND.getHttpStatus());
    }

    @Test
    void allErrorCodesShouldHaveMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getMessage(), "ErrorCode " + code + " should have a message");
        }
    }

    /**
     * P2 错误承载纠正（2026-10-18，批次 B7b）—— 按 {@code collaboration/09-constraints.md} §三·六：
     * <b>资源不存在 = 真 404 + {@code NO_RESOURCE_FOUND(1103)}</b>；只有<b>业务规则拒绝</b>才用 200 信封。
     */
    @Test
    @DisplayName("P2 纠正 — 商品不存在统一 1103 / 404；3011 的状态也被纠正为 404（不得再退化成 200）")
    @SuppressWarnings("deprecation")
    void shouldCarryNotFoundAsReal404() {
        assertEquals(1103, ErrorCode.NO_RESOURCE_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NO_RESOURCE_FOUND.getHttpStatus());
        // 已退役的 3011：状态被纠正为 404 —— 即使有遗漏调用也不会伪装成「请求成功」
        assertEquals(3011, ErrorCode.PRODUCT_NOT_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND.getHttpStatus(),
                "not-found 语义不得用 200 信封承载（09 §三·六 定性与纪律 1/2）");
    }

    @Test
    @DisplayName("业务规则拒绝 — 购物车条目超限仍是 200 信封（3042），与「资源不存在」区分开")
    void shouldKeepBusinessRuleRejectionAs200() {
        assertEquals(3042, ErrorCode.CART_ITEM_LIMIT_EXCEEDED.getCode());
        assertEquals(HttpStatus.OK, ErrorCode.CART_ITEM_LIMIT_EXCEEDED.getHttpStatus());
    }

    /**
     * 登录失败口径（2026-10-18 批次 ④，抗账号枚举）。
     *
     * <p><b>锁死的缺陷（修复前会失败）</b>：{@code PASSWORD_ERROR} 原配 {@code HttpStatus.OK}（200）、
     * {@code USER_STATUS_ABNORMAL} 原配 200、{@code ACCOUNT_FROZEN} 原配 403，而「账号不存在」走
     * {@code USER_NOT_FOUND(1102) / 404} —— <b>只看状态码</b>即可枚举「某账号是否存在 / 是否被冻结」。
     */
    @Test
    @DisplayName("登录失败 — 3007/3008/1017 一律 401，且 3007 的默认文案中性（不得区分原因）")
    @SuppressWarnings("deprecation")
    void shouldCarryLoginFailureAs401() {
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.PASSWORD_ERROR.getHttpStatus(),
                "登录失败必须 401（09-constraints §三·六「未认证 → 401」）");
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.USER_STATUS_ABNORMAL.getHttpStatus(),
                "状态不可登录也是登录失败，不得用 200 与「账号不存在」的 404 形成差异");
        assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_FROZEN.getHttpStatus(),
                "风控冻结已退役到 3007；遗留枚举也不得退化成可区分的 403");

        assertEquals("账号或密码不正确", ErrorCode.PASSWORD_ERROR.getMessage(),
                "默认文案必须中性：调用方漏传自定义文案时不得泄漏「账号不存在 / 密码错 / 被冻结」");
    }

    @Test
    @DisplayName("登录以外的「用户不存在」不受影响 — USER_NOT_FOUND 仍是 404（不得全局改口径）")
    void shouldKeepUserNotFoundAs404OutsideLoginPath() {
        assertEquals(1102, ErrorCode.USER_NOT_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND.getHttpStatus(),
                "USER_NOT_FOUND 仍被重置密码/商家/会员等非登录场景使用，只有登录链路改为 3007/401");
    }

    // ==================== 批次 ①：not-found 码状态归位 200 → 404 ====================

    /**
     * 批次 ① 的<b>正向</b>断言（表驱动，仿 {@code PRODUCT_NOT_FOUND} 先例）：
     * 12 个 not-found 语义的错误码必须配 <b>真 404</b>。
     *
     * <p><b>逐条覆盖为什么必要</b>：本批纠正的正是「用 200 信封承载 not-found」的偏离
     * （{@code collaboration/09-constraints.md} §三·六 第 2 条）。逐个码断言而不是只断言
     * 「整体没有 200 的 not-found」，是因为后者的失效形态是<b>新增一个漏改的码</b> ——
     * 表驱动才能在失败信息里直接点名是哪个码退化了。
     *
     * <p><b>同时钉死「只改状态码、业务码不变」</b>：码值与文案一字未动，否则前端按码区分的
     * 「订单不存在 / 店铺不存在 / SKU 不存在」会失去意义（合并成单一 1103 会丢信息）。
     */
    @Test
    @DisplayName("批次 ① — 12 个 not-found 码一律 404，且业务码/文案保持不变")
    @SuppressWarnings("deprecation")
    void shouldCarryEveryNotFoundCodeAsReal404() {
        // 码 → 期望业务码（键 = 枚举，值 = 业务码；文案另断言非空，见 allErrorCodesShouldHaveMessage）
        Map<ErrorCode, Integer> notFoundCodes = new LinkedHashMap<>();
        notFoundCodes.put(ErrorCode.ORDER_NOT_FOUND, 3009);
        notFoundCodes.put(ErrorCode.SHOP_NOT_FOUND, 3015);
        notFoundCodes.put(ErrorCode.SKU_NOT_FOUND, 3016);
        notFoundCodes.put(ErrorCode.HOME_CONFIG_NOT_FOUND, 3017);
        notFoundCodes.put(ErrorCode.COUPON_NOT_FOUND, 3019);
        notFoundCodes.put(ErrorCode.FLASH_SALE_NOT_FOUND, 3020);
        notFoundCodes.put(ErrorCode.APPLICATION_NOT_FOUND, 3032);
        notFoundCodes.put(ErrorCode.WITHDRAWAL_NOT_FOUND, 3033);
        notFoundCodes.put(ErrorCode.NOTIFICATION_NOT_FOUND, 3034);
        notFoundCodes.put(ErrorCode.LOGISTICS_NOT_FOUND, 3035);
        // 死码（零生产引用）：只改状态码，不启用、不删除 —— 见 D 项登记
        notFoundCodes.put(ErrorCode.REVIEW_NOT_FOUND, 3036);
        notFoundCodes.put(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, 3041);

        for (Map.Entry<ErrorCode, Integer> entry : notFoundCodes.entrySet()) {
            ErrorCode code = entry.getKey();
            assertEquals(entry.getValue(), code.getCode(),
                    code.name() + " 的业务码不得因状态码归位而变化（前端按码区分不同资源）");
            assertEquals(HttpStatus.NOT_FOUND, code.getHttpStatus(),
                    code.name() + " 是 not-found 语义，必须真 404（09 §三·六：不得用 200 信封承载）");
            assertEquals(404, code.getHttpStatus().value(), code.name() + " 状态码必须是 404");
            // 404 → CLIENT（ErrorCategory 按状态码派生）：Feign 调用方据此原样透传，不改写成 SYSTEM_BUSY
            assertSame(ErrorCategory.CLIENT, ErrorCategory.of(code),
                    code.name() + " 在 Feign 侧必须判 CLIENT（透传业务码/文案），而不是 RULE/TRANSIENT");
            assertFalse(ErrorCategory.of(code).isRetryable(),
                    code.name() + " 是确定性失败，重试只会放大故障");
        }

        // 1103 是 not-found 家族的「统一出口」，本批已对，回归断言防它被顺手改回 200
        assertEquals(1103, ErrorCode.NO_RESOURCE_FOUND.getCode());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NO_RESOURCE_FOUND.getHttpStatus());
    }

    /**
     * 批次 ① 的<b>反向</b>断言（防日后误改）：那些<b>不是</b> not-found 语义的码
     * <b>不得</b>被顺手改成 404。
     *
     * <p>这四类正是批次 ③ 刚从「not-found 双义码」里拆出去的分支，也是本批明令不得触碰的分支：
     * <ul>
     *   <li>{@code 3000} 业务规则拒绝（店铺审核中/冻结、内置角色不可删…）→ 200 / {@code RULE}；</li>
     *   <li>{@code 1013} 无权限（店铺归属他人）→ 403；</li>
     *   <li>{@code 1101} 参数错误（SKU 业务键形态非法）→ 400；</li>
     *   <li>{@code 3008} 账号状态异常（非登录域）→ 401。</li>
     * </ul>
     * 语义各不相同，一旦被改成 404，「资源存在但你没权 / 参数错 / 账号异常」就会伪装成
     * 「没有这个东西」。
     *
     * <p><b>注意断言口径</b>：{@code 403}/{@code 400}/{@code 401} 在 {@code ErrorCategory} 里
     * <b>本来就都是 {@code CLIENT}</b>（4xx ⇒ CLIENT），所以「不是 CLIENT」<b>不能</b>用来表达
     * 「不是 not-found」—— 判据只能是 <b>HTTP 状态码 ≠ 404</b>。只有 {@code 3000} 才额外要求
     * 「category 仍是 RULE」（200 信封 = 业务规则拒绝）。
     */
    @Test
    @DisplayName("批次 ① 反向 — 3000/1013/1101/3008 一律不得是 404（防误改）")
    void shouldNeverTurnRuleOrForbiddenInto404() {
        Map<ErrorCode, HttpStatus> mustNotBe404 = new LinkedHashMap<>();
        mustNotBe404.put(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, HttpStatus.OK);   // RULE → 200
        mustNotBe404.put(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);               // 403
        mustNotBe404.put(ErrorCode.PARAM_ERROR, HttpStatus.BAD_REQUEST);           // 400
        mustNotBe404.put(ErrorCode.USER_STATUS_ABNORMAL, HttpStatus.UNAUTHORIZED); // 401

        for (Map.Entry<ErrorCode, HttpStatus> entry : mustNotBe404.entrySet()) {
            ErrorCode code = entry.getKey();
            assertNotEquals(HttpStatus.NOT_FOUND, code.getHttpStatus(),
                    code.name() + " 不是 not-found 语义，改成 404 会把规则拒绝/越权/参数错伪装成「没有」");
            assertNotEquals(404, code.getHttpStatus().value(),
                    code.name() + " 的状态码不得漂移成 404");
            assertEquals(entry.getValue(), code.getHttpStatus(),
                    code.name() + " 的状态码不得漂移（批次 ③ 拆出的分支必须保持原承载）");
        }

        // 其中「业务规则拒绝」的代表项必须仍是 RULE（200 信封），这是与 404 家族的根本分界
        assertSame(ErrorCategory.RULE, ErrorCategory.of(ErrorCode.BUSINESS_EXECUTION_EXCEPTION));
        assertEquals(200, ErrorCode.BUSINESS_EXECUTION_EXCEPTION.getHttpStatus().value());

        // 附带：业务规则拒绝的代表项仍是 200 信封（与 404 家族划清界限）
        assertEquals(HttpStatus.OK, ErrorCode.CART_ITEM_LIMIT_EXCEEDED.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.FLASH_SALE_NOT_STARTED.getHttpStatus());
        assertEquals(HttpStatus.OK, ErrorCode.AFTER_SALE_EXISTS.getHttpStatus());
    }
}
