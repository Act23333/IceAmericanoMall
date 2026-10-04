package org.icedamericanomall.logistics.domain.model.logistics;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.logistics.domain.enums.LogisticsStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 物流包裹聚合单元测试 —— 工厂缺省语义与单调状态迁移（业务规则内聚于聚合）。
 */
@DisplayName("物流包裹聚合测试")
class PackageTest {

    private Package pkg(int status) {
        return Package.rehydrate(1L, 1L, 1, "SF123456", "顺丰快递", null, null,
                null, null, null, null, null, status, null, null);
    }

    // ==================== 工厂 ====================

    @Test
    @DisplayName("create — 状态默认为待揽收")
    void shouldDefaultStatusAsPending_whenCreating() {
        Package pkg = Package.create(1L, 1, "SF123456", "顺丰快递", null, null,
                null, null, null, null, null);

        assertEquals(LogisticsStatusEnum.PENDING.getCode(), pkg.getStatus());
        assertEquals(1, pkg.getPackageNo());
        assertEquals("SF123456", pkg.getLogisticsNumber());
    }

    @Test
    @DisplayName("create — packageNo 未传时按 1 兜底（单包裹语义兼容）")
    void shouldDefaultPackageNoToOne_whenAbsent() {
        Package pkg = Package.create(1L, null, "SF123456", "顺丰快递", null, null,
                null, null, null, null, null);

        assertEquals(1, pkg.getPackageNo());
    }

    @Test
    @DisplayName("rehydrate — 仓储重建字段完整回填")
    void shouldRehydrateAllFields() {
        Package pkg = Package.rehydrate(9L, 1L, 2, "SF123", "顺丰", null, null,
                null, null, null, null, null,
                LogisticsStatusEnum.SHIPPED.getCode(), null, null);

        assertEquals(9L, pkg.getId());
        assertEquals(2, pkg.getPackageNo());
        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), pkg.getStatus());
    }

    // ==================== transitTo（状态迁移内聚） ====================

    @Test
    @DisplayName("transitTo — 待揽收 → 运输中（合法迁移）")
    void shouldTransitFromPendingToShipped() {
        Package pkg = pkg(LogisticsStatusEnum.PENDING.getCode());

        pkg.transitTo(LogisticsStatusEnum.SHIPPED);

        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), pkg.getStatus());
    }

    @Test
    @DisplayName("transitTo — 同状态重放：幂等空操作（状态不变，不抛异常）")
    void shouldBeNoOp_whenSameStatus() {
        Package pkg = pkg(LogisticsStatusEnum.SHIPPED.getCode());

        boolean changed = pkg.transitTo(LogisticsStatusEnum.SHIPPED);

        assertFalse(changed, "同状态重放应返回 false（幂等空操作）");
        assertEquals(LogisticsStatusEnum.SHIPPED.getCode(), pkg.getStatus());
    }

    @Test
    @DisplayName("transitTo — 非法迁移（待揽收→已签收）抛 ORDER_STATUS_INVALID 且状态不变")
    void shouldThrowStatusInvalid_whenTransitionIllegal() {
        Package pkg = pkg(LogisticsStatusEnum.PENDING.getCode());

        BizException ex = assertThrows(BizException.class,
                () -> pkg.transitTo(LogisticsStatusEnum.DELIVERED));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        assertEquals(LogisticsStatusEnum.PENDING.getCode(), pkg.getStatus());
    }

    @Test
    @DisplayName("transitTo — 状态回退（运输中→待揽收）被拒绝")
    void shouldRejectStatusRollback() {
        Package pkg = pkg(LogisticsStatusEnum.SHIPPED.getCode());

        assertThrows(BizException.class, () -> pkg.transitTo(LogisticsStatusEnum.PENDING));
    }

    @Test
    @DisplayName("transitTo — 已签收↔已退回 双向迁移合法")
    void shouldAllowDeliveredReturnedRoundTrip() {
        Package pkg = pkg(LogisticsStatusEnum.DELIVERED.getCode());

        assertTrue(pkg.transitTo(LogisticsStatusEnum.RETURNED));
        assertEquals(LogisticsStatusEnum.RETURNED.getCode(), pkg.getStatus());

        assertTrue(pkg.transitTo(LogisticsStatusEnum.DELIVERED));
        assertEquals(LogisticsStatusEnum.DELIVERED.getCode(), pkg.getStatus());
    }

    @Test
    @DisplayName("transitTo — 聚合当前状态非法（脏数据）→ PARAM_ERROR")
    void shouldThrowParamError_whenCurrentStatusCorrupted() {
        Package pkg = Package.rehydrate(9L, 1L, 1, "SF123", "顺丰", null, null,
                null, null, null, null, null, 99, null, null);

        BizException ex = assertThrows(BizException.class,
                () -> pkg.transitTo(LogisticsStatusEnum.SHIPPED));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    // ==================== LogisticsStatusEnum ====================

    @Test
    @DisplayName("LogisticsStatusEnum — 状态码正确映射且共 4 个")
    void shouldHaveCorrectLogisticsStatusCodes() {
        assertEquals(1, LogisticsStatusEnum.PENDING.getCode(), "待揽收");
        assertEquals(2, LogisticsStatusEnum.SHIPPED.getCode(), "运输中");
        assertEquals(3, LogisticsStatusEnum.DELIVERED.getCode(), "已签收");
        assertEquals(4, LogisticsStatusEnum.RETURNED.getCode(), "已退回");
        assertEquals(4, LogisticsStatusEnum.values().length);
    }

    @Test
    @DisplayName("LogisticsStatusEnum.of — 无效 code 返回 null")
    void shouldReturnNull_whenInvalidCode() {
        assertNull(LogisticsStatusEnum.of(null));
        assertNull(LogisticsStatusEnum.of(99));
        assertNull(LogisticsStatusEnum.of(0));
    }

    @Test
    @DisplayName("LogisticsStatusEnum.of — 根据 code 查找枚举")
    void shouldFindEnumByCode() {
        assertEquals(LogisticsStatusEnum.PENDING, LogisticsStatusEnum.of(1));
        assertEquals(LogisticsStatusEnum.SHIPPED, LogisticsStatusEnum.of(2));
        assertEquals(LogisticsStatusEnum.DELIVERED, LogisticsStatusEnum.of(3));
        assertEquals(LogisticsStatusEnum.RETURNED, LogisticsStatusEnum.of(4));
    }

    @Test
    @DisplayName("LogisticsStatusEnum — 合理迁移路径存在，非法回退不存在")
    void shouldSupportValidStateTransitions() {
        assertTrue(LogisticsStatusEnum.PENDING.canTransitTo(LogisticsStatusEnum.SHIPPED));
        assertTrue(LogisticsStatusEnum.SHIPPED.canTransitTo(LogisticsStatusEnum.DELIVERED));
        assertFalse(LogisticsStatusEnum.DELIVERED.canTransitTo(LogisticsStatusEnum.PENDING));
    }
}
