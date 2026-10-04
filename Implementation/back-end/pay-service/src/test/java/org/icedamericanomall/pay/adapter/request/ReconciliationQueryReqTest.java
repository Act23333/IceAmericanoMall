package org.icedamericanomall.pay.adapter.request;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 对账查询/触发入参测试（F4）—— 锁死 AD46 的 {@code billDate} 校验口径：
 * 缺失或格式非法一律 400 {@code PARAM_ERROR}(1101)，而不是让 Spring 抛出不受控的绑定异常。
 */
@DisplayName("ReconciliationQueryReq — billDate 解析与校验")
class ReconciliationQueryReqTest {

    @Test
    @DisplayName("必填场景 — 缺失 → PARAM_ERROR(400)")
    void shouldReject_whenRequiredAndMissing() {
        ReconciliationQueryReq req = new ReconciliationQueryReq();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> req.parseBillDate(true));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("必填场景 — 格式非法 → PARAM_ERROR(400)")
    void shouldReject_whenIllegalFormat() {
        ReconciliationQueryReq req = new ReconciliationQueryReq();
        req.setBillDate("2026-13-99");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> req.parseBillDate(true));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("必填场景 — 合法日期解析成功")
    void shouldParse_whenValid() {
        ReconciliationQueryReq req = new ReconciliationQueryReq();
        req.setBillDate("2026-10-02");

        assertEquals(LocalDate.of(2026, 10, 2), req.parseBillDate(true));
    }

    @Test
    @DisplayName("选填场景 — 缺失返回 null（AD44 不过滤账单日）")
    void shouldReturnNull_whenOptionalAndMissing() {
        assertNull(new ReconciliationQueryReq().parseBillDate(false));
    }
}
