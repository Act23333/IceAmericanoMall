package org.icedamericanomall.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.icedamericanomall.common.enums.ErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void shouldReturnSuccessWithData() {
        Result<Integer> result = Result.ok(42);
        assertEquals(200, result.getCode());
        assertEquals(42, result.getData().intValue());
    }

    @Test
    void shouldReturnSuccessWithoutData() {
        Result<Void> result = Result.ok();
        assertEquals(200, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnSuccessWithMessageAndData() {
        Result<String> result = Result.ok("success", "data");
        assertEquals(200, result.getCode());
        assertEquals("success", result.getMsg());
        assertEquals("data", result.getData());
    }

    @Test
    void shouldReturnErrorWithCodeAndMessage() {
        Result<Void> result = Result.error(400, "bad request");
        assertEquals(400, result.getCode());
        assertEquals("bad request", result.getMsg());
        assertNull(result.getData());
    }

    @Test
    void shouldReturnErrorFromCommonException() {
        Result<Void> result = Result.error(
                new org.icedamericanomall.common.exception.CommonException(ErrorCode.USER_NOT_FOUND) {});
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    @DisplayName("success(...) 与 ok(...) 等价（AGENTS.md 契约命名，向后兼容）")
    void shouldDelegateSuccessToOk() {
        Result<Object> empty = Result.success((Object) null);
        assertEquals(Result.SUCCESS_CODE, empty.getCode());
        assertEquals("success", empty.getMsg());
        assertNull(empty.getData());

        assertEquals("hi", Result.success("hi").getMsg());
        assertEquals(42, Result.success(42).getData().intValue());
        assertEquals(42, Result.success("with-data", 42).getData().intValue());
        assertTrue(Result.success(42).success());
    }
}
