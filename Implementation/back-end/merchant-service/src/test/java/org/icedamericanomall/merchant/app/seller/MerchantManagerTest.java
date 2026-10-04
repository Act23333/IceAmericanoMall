package org.icedamericanomall.merchant.app.seller;

import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.adapter.request.MerchantApplyReq;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.icedamericanomall.merchant.domain.service.MerchantRegistrar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家管理编排单元测试 —— 入驻申请（商家 + 默认店铺）、审批走状态机 + 卖家角色授予、
 * 非法状态迁移拒绝、商家不存在拒绝。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantManager 商家编排单元测试")
class MerchantManagerTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MerchantRegistrar merchantRegistrar;

    @Mock
    private AuthContextClient authContextClient;

    @InjectMocks
    private MerchantManager merchantManager;

    private MerchantApplyReq applyReq() {
        MerchantApplyReq req = new MerchantApplyReq();
        req.setShopName("冰美式旗舰店");
        req.setContactPhone("138****8000");
        req.setProvince("上海市");
        req.setCity("上海市");
        req.setDistrict("静安区");
        req.setDetailAddress("南京西路 100 号");
        return req;
    }

    @Test
    @DisplayName("入驻申请 — 组装商家身份 + 默认店铺（均审核中）并委托领域服务")
    void shouldDelegateApply_withMerchantAndDefaultShop() {
        merchantManager.register(100L, applyReq());

        ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
        ArgumentCaptor<Shop> shopCaptor = ArgumentCaptor.forClass(Shop.class);
        verify(merchantRegistrar).register(merchantCaptor.capture(), shopCaptor.capture());

        assertEquals(100L, merchantCaptor.getValue().getUserId());
        assertEquals(MerchantStatusEnum.PENDING, merchantCaptor.getValue().getStatus());
        assertEquals("冰美式旗舰店", shopCaptor.getValue().getShopName());
        assertEquals("138****8000", shopCaptor.getValue().getContactPhone());
        assertEquals("南京西路 100 号", shopCaptor.getValue().getDetailAddress());
        assertEquals(ShopStatusEnum.PENDING, shopCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("审批 — 商家 + 默认店铺状态置正常 + 经授权域授予卖家角色（roleType=1）")
    void shouldApproveMerchantAndDefaultShop_andGrantSellerRole() {
        Merchant merchant = Merchant.builder()
                .id(1L).userId(100L).status(MerchantStatusEnum.PENDING).build();
        Shop shop = Shop.builder()
                .id(5L).merchantId(1L).status(ShopStatusEnum.PENDING).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));
        when(shopRepository.findDefaultByMerchantId(1L)).thenReturn(Optional.of(shop));

        merchantManager.approve(1L);

        assertEquals(MerchantStatusEnum.NORMAL, merchant.getStatus());
        verify(merchantRepository).updateStatus(merchant);
        assertEquals(ShopStatusEnum.NORMAL, shop.getStatus(), "默认店铺同步生效");
        verify(shopRepository).updateStatus(shop);
        verify(authContextClient).setUserRole(100L, 1);
    }

    @Test
    @DisplayName("审批 — 商家不存在 → USER_NOT_FOUND，不授予角色、不改店铺")
    void shouldReject_whenMerchantAbsent() {
        when(merchantRepository.findById(9L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> merchantManager.approve(9L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        verify(authContextClient, never()).setUserRole(eq(9L), anyInt());
        verify(shopRepository, never()).findDefaultByMerchantId(any());
    }

    @Test
    @DisplayName("状态变更 — 目标为审核中（非法回退）→ PARAM_ERROR，不落库")
    void shouldRejectIllegalTargetStatus_whenUpdateStatusToPending() {
        Merchant merchant = Merchant.builder()
                .id(1L).userId(100L).status(MerchantStatusEnum.NORMAL).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        BizException ex = assertThrows(BizException.class,
                () -> merchantManager.updateStatus(1L, MerchantStatusEnum.PENDING.getCode()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(merchantRepository, never()).updateStatus(any(Merchant.class));
    }

    @Test
    @DisplayName("状态变更 — 已冻结商家置正常 → 走解冻迁移")
    void shouldUnfreeze_whenFrozenAndTargetNormal() {
        Merchant frozen = Merchant.builder()
                .id(1L).userId(100L).status(MerchantStatusEnum.FROZEN).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(frozen));

        merchantManager.updateStatus(1L, MerchantStatusEnum.NORMAL.getCode());

        assertEquals(MerchantStatusEnum.NORMAL, frozen.getStatus());
        verify(merchantRepository).updateStatus(frozen);
    }

    @Test
    @DisplayName("状态变更 — 置冻结 → 走冻结迁移")
    void shouldFreeze_whenTargetFrozen() {
        Merchant merchant = Merchant.builder()
                .id(1L).userId(100L).status(MerchantStatusEnum.NORMAL).build();
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant));

        merchantManager.updateStatus(1L, MerchantStatusEnum.FROZEN.getCode());

        assertEquals(MerchantStatusEnum.FROZEN, merchant.getStatus());
        verify(merchantRepository).updateStatus(merchant);
    }
}
