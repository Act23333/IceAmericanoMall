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
}
