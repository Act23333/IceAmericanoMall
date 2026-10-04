package org.icedamericanomall.merchant.domain.model.merchant;

import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商家聚合根纯单元测试 —— 状态机（审核/冻结/解冻）。
 *
 * <p>ADR-019 后店铺资料已拆到 {@link Shop}，本聚合只验证身份 + 生命周期。
 */
@DisplayName("Merchant 聚合根领域单元测试")
class MerchantTest {

    @Test
    @DisplayName("apply — 入驻申请状态为 PENDING")
    void shouldBePending_whenApply() {
        Merchant merchant = Merchant.apply(100L);
        assertEquals(MerchantStatusEnum.PENDING, merchant.getStatus());
        assertEquals(100L, merchant.getUserId());
        assertTrue(merchant.isPending());
    }

    @Test
    @DisplayName("approve — PENDING→NORMAL 且幂等")
    void shouldApprove_andIdempotent() {
        Merchant merchant = Merchant.apply(100L);
        merchant.approve();
        assertTrue(merchant.isActive());
        merchant.approve();
        assertTrue(merchant.isActive(), "重复审核通过幂等");
    }

    @Test
    @DisplayName("freeze — 任意状态→FROZEN 且幂等")
    void shouldFreeze_andIdempotent() {
        Merchant merchant = Merchant.apply(100L);
        merchant.freeze();
        assertTrue(merchant.isFrozen());
        merchant.freeze();
        assertTrue(merchant.isFrozen(), "重复冻结幂等");
    }

    @Test
    @DisplayName("unfreeze — 仅已冻结商家可解冻")
    void shouldUnfreeze_onlyWhenFrozen() {
        Merchant merchant = Merchant.apply(100L);
        assertThrows(BizException.class, merchant::unfreeze, "审核中商家不可解冻");

        merchant.freeze();
        merchant.unfreeze();
        assertTrue(merchant.isActive());
    }

    @Test
    @DisplayName("MerchantStatusEnum — 状态码映射与非法值拒绝")
    void shouldMapStatusCodes() {
        assertEquals(0, MerchantStatusEnum.PENDING.getCode());
        assertEquals(1, MerchantStatusEnum.NORMAL.getCode());
        assertEquals(2, MerchantStatusEnum.FROZEN.getCode());
        assertEquals(MerchantStatusEnum.NORMAL, MerchantStatusEnum.fromCode(1));
        assertNull(MerchantStatusEnum.fromCode(null));
        assertFalse(MerchantStatusEnum.fromCode(0) == MerchantStatusEnum.NORMAL);
        assertThrows(BizException.class, () -> MerchantStatusEnum.fromCode(99));
    }
}
