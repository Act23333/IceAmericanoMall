package org.noLazy.common.domain;

import org.junit.jupiter.api.Test;
import org.noLazy.common.enums.ErrorCode;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void shouldReturnSuccessWithData() {
        Result<Integer> result = Result.ok(42);
        assertEquals(200, result.getCode());
        assertEquals(42, result.getData());
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
                new org.noLazy.common.exception.CommonException(ErrorCode.USER_NOT_FOUND) {});
        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), result.getCode());
    }
}
