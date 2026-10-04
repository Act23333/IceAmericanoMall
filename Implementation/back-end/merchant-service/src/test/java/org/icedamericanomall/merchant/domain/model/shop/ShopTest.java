package org.icedamericanomall.merchant.domain.model.shop;

import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺聚合根纯单元测试 —— 开店校验、归属回填、状态机（审核/冻结/解冻）与资料更新。
 */
@DisplayName("Shop 聚合根领域单元测试")
class ShopTest {

    private static final String NAME = "冰美式旗舰店";
    private static final String PHONE = "13800008000";
    private static final String PROVINCE = "上海市";
    private static final String CITY = "上海市";
    private static final String DISTRICT = "静安区";
    private static final String DETAIL = "南京西路 100 号";

    private Shop shop() {
        return Shop.open(NAME, null, PHONE, PROVINCE, CITY, DISTRICT, DETAIL);
    }

    @Test
    @DisplayName("open — 开店初始状态为 PENDING，资料落值")
    void shouldBePending_whenOpen() {
        Shop shop = shop();

        assertEquals(ShopStatusEnum.PENDING, shop.getStatus());
        assertEquals(NAME, shop.getShopName());
        assertEquals(PHONE, shop.getContactPhone());
        assertEquals(DETAIL, shop.getDetailAddress());
        assertNull(shop.getId(), "落库前无技术主键");
    }

    @Test
    @DisplayName("open — 必填资料缺失 → PARAM_ERROR（创建即校验）")
    void shouldReject_whenRequiredProfileMissing() {
        assertThrows(BizException.class,
                () -> Shop.open(" ", null, PHONE, PROVINCE, CITY, DISTRICT, DETAIL), "店名空白");
        assertThrows(BizException.class,
                () -> Shop.open(NAME, null, null, PROVINCE, CITY, DISTRICT, DETAIL), "联系电话缺失");
        assertThrows(BizException.class,
                () -> Shop.open(NAME, null, PHONE, PROVINCE, CITY, null, DETAIL), "区县缺失");
    }

    @Test
    @DisplayName("assignMerchant — 回填归属；重复同 ID 幂等，改属其他商家拒绝")
    void shouldAssignMerchant_andRejectReassign() {
        Shop shop = shop();

        shop.assignMerchant(9L);
        assertEquals(9L, shop.getMerchantId());

        shop.assignMerchant(9L);
        assertEquals(9L, shop.getMerchantId(), "同 ID 重复回填幂等");

        assertThrows(BizException.class, () -> shop.assignMerchant(10L), "已归属不可改属");
        assertThrows(BizException.class, () -> shop().assignMerchant(null), "归属不可为空");
    }

    @Test
    @DisplayName("approve — PENDING→NORMAL 且幂等")
    void shouldApprove_andIdempotent() {
        Shop shop = shop();

        shop.approve();
        assertTrue(shop.isNormal());
        shop.approve();
        assertTrue(shop.isNormal(), "重复审核通过幂等");
    }

    @Test
    @DisplayName("freeze — 任意状态→FROZEN 且幂等")
    void shouldFreeze_andIdempotent() {
        Shop shop = shop();

        shop.freeze();
        assertFalse(shop.isNormal());
        assertEquals(ShopStatusEnum.FROZEN, shop.getStatus());
        shop.freeze();
        assertEquals(ShopStatusEnum.FROZEN, shop.getStatus(), "重复冻结幂等");
    }

    @Test
    @DisplayName("unfreeze — 仅已冻结店铺可解冻")
    void shouldUnfreeze_onlyWhenFrozen() {
        Shop shop = shop();
        assertThrows(BizException.class, shop::unfreeze, "审核中店铺不可解冻");

        shop.freeze();
        shop.unfreeze();
        assertTrue(shop.isNormal());
    }

    @Test
    @DisplayName("updateProfile — null 字段不覆盖，非 null 覆盖")
    void shouldUpdateProfile_nullSafe() {
        Shop shop = shop();
        shop.setShopName("旧店名");
        shop.setCity("深圳市");

        shop.updateProfile("新店名", "logo.png", null, null, "广州市", null, null);

        assertEquals("新店名", shop.getShopName());
        assertEquals("logo.png", shop.getShopLogo());
        assertEquals("广州市", shop.getCity());
        assertEquals(PHONE, shop.getContactPhone(), "null 字段不写入");
    }

    @Test
    @DisplayName("ShopStatusEnum — 状态码映射与非法值拒绝")
    void shouldMapStatusCodes() {
        assertEquals(0, ShopStatusEnum.PENDING.getCode());
        assertEquals(1, ShopStatusEnum.NORMAL.getCode());
        assertEquals(2, ShopStatusEnum.FROZEN.getCode());
        assertEquals(ShopStatusEnum.NORMAL, ShopStatusEnum.fromCode(1));
        assertNull(ShopStatusEnum.fromCode(null));
        assertThrows(BizException.class, () -> ShopStatusEnum.fromCode(99));
    }
}
