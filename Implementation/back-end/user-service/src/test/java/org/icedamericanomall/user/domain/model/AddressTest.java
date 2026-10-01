package org.icedamericanomall.user.domain.model;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收货地址聚合根纯单元测试 —— 归属校验（越权防范）与默认地址迁移。
 */
@DisplayName("Address 聚合根领域单元测试")
class AddressTest {

    @Test
    @DisplayName("assertOwnedBy — 本人地址放行")
    void shouldPass_whenOwned() {
        Address address = Address.builder().userId(100L).build();
        address.assertOwnedBy(100L);
    }

    @Test
    @DisplayName("assertOwnedBy — 他人地址抛 ILLEGAL_REQUEST（越权防范）")
    void shouldThrow_whenNotOwned() {
        Address address = Address.builder().userId(100L).build();
        BizException e = assertThrows(BizException.class, () -> address.assertOwnedBy(200L));
        assertEquals(ErrorCode.ILLEGAL_REQUEST, e.getErrorCode());
    }

    @Test
    @DisplayName("assertOwnedBy — 未登录（userId=null）同样拒绝")
    void shouldThrow_whenCurrentUserNull() {
        Address address = Address.builder().userId(100L).build();
        assertThrows(BizException.class, () -> address.assertOwnedBy(null));
    }

    @Test
    @DisplayName("markDefault / unmarkDefault — 默认标记迁移刷新 updateTime")
    void shouldToggleDefault() {
        Address address = Address.builder().userId(100L).defaulted(false).build();
        address.markDefault();
        assertTrue(address.getDefaulted());
        assertNotNull(address.getUpdateTime());

        address.unmarkDefault();
        assertFalse(address.getDefaulted());
    }
}
