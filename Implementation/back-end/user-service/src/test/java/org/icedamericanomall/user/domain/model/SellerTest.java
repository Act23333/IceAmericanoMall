package org.icedamericanomall.user.domain.model;

import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商家聚合根纯单元测试 —— 状态机（审核/冻结/解冻）与 null-safe 店铺信息更新。
 */
@DisplayName("Seller 聚合根领域单元测试")
class SellerTest {

    @Test
    @DisplayName("apply — 入驻申请状态为 PENDING")
    void shouldBePending_whenApply() {
        Seller seller = Seller.apply(100L);
        assertEquals(SellerStatusEnum.PENDING, seller.getStatus());
        assertEquals(100L, seller.getUserId());
        assertTrue(seller.isPending());
    }

    @Test
    @DisplayName("approve — PENDING→NORMAL 且幂等")
    void shouldApprove_andIdempotent() {
        Seller seller = Seller.apply(100L);
        seller.approve();
        assertTrue(seller.isActive());
        seller.approve();
        assertTrue(seller.isActive(), "重复审核通过幂等");
    }

    @Test
    @DisplayName("freeze — 任意状态→FROZEN 且幂等")
    void shouldFreeze_andIdempotent() {
        Seller seller = Seller.apply(100L);
        seller.freeze();
        assertTrue(seller.isFrozen());
        seller.freeze();
        assertTrue(seller.isFrozen(), "重复冻结幂等");
    }

    @Test
    @DisplayName("unfreeze — 仅已冻结商家可解冻")
    void shouldUnfreeze_onlyWhenFrozen() {
        Seller seller = Seller.apply(100L);
        assertThrows(BizException.class, seller::unfreeze, "审核中商家不可解冻");

        seller.freeze();
        seller.unfreeze();
        assertTrue(seller.isActive());
    }

    @Test
    @DisplayName("updateShopInfo — null 字段不覆盖，非 null 覆盖")
    void shouldUpdateShopInfo_nullSafe() {
        Seller seller = Seller.apply(100L);
        seller.setShopName("旧店名");
        seller.setCity("深圳市");

        seller.updateShopInfo("新店名", null, null, null, "广州市", null, null);
        assertEquals("新店名", seller.getShopName());
        assertEquals("广州市", seller.getCity());
        assertNull(seller.getShopLogo(), "null 字段不写入");
    }

    @Test
    @DisplayName("SellerStatusEnum — 状态码映射与非法值拒绝")
    void shouldMapStatusCodes() {
        assertEquals(0, SellerStatusEnum.PENDING.getCode());
        assertEquals(1, SellerStatusEnum.NORMAL.getCode());
        assertEquals(2, SellerStatusEnum.FROZEN.getCode());
        assertEquals(SellerStatusEnum.NORMAL, SellerStatusEnum.fromCode(1));
        assertNull(SellerStatusEnum.fromCode(null));
        assertFalse(SellerStatusEnum.fromCode(0) == SellerStatusEnum.NORMAL);
        assertThrows(BizException.class, () -> SellerStatusEnum.fromCode(99));
    }
}
