package org.icedamericanomall.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
