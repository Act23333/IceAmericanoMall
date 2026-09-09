package org.noLazy.common.enums;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void shouldHaveCorrectCodeForEachError() {
        assertEquals(1000, ErrorCode.INTERNAL_ERROR.getCode());
        assertEquals(1001, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(1002, ErrorCode.TOKEN_EXPIRED.getCode());
        assertEquals(1003, ErrorCode.FORBIDDEN.getCode());
        assertEquals(1004, ErrorCode.FREQUENT_ERROR.getCode());
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
}
