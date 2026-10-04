package org.icedamericanomall.merchant.app.seller;

import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店铺关注编排单元测试 —— shopId 记账、遗留 sellerId 解析、站内信通知尽力而为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StoreFollowManager 店铺关注编排单元测试")
class StoreFollowManagerTest {

    @Mock
    private StoreFollowStore storeFollowStore;

    @Mock
    private ShopManager shopManager;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private StoreFollowManager storeFollowManager;

    private ShopVO shopVO(Long shopId, Long sellerId) {
        ShopVO vo = new ShopVO();
        vo.setShopId(shopId);
        vo.setSellerId(sellerId);
        vo.setShopName("冰美式旗舰店");
        return vo;
    }

    @Test
    @DisplayName("关注 — shopId 口径：按 shopId 记账 + 通知店铺主（linkUrl 用 shopId）")
    void shouldFollowAndNotifyOwner_whenShopIdProvided() {
        when(shopManager.resolveShopId(5L, null)).thenReturn(5L);
        when(shopManager.getVisibleShop(5L)).thenReturn(shopVO(5L, 100L));
        when(storeFollowStore.follow(200L, 5L)).thenReturn(3L);

        long count = storeFollowManager.follow(200L, 5L, null);

        assertEquals(3L, count);
        verify(storeFollowStore).follow(200L, 5L);

        ArgumentCaptor<InAppMessageSendReqCO> captor = ArgumentCaptor.forClass(InAppMessageSendReqCO.class);
        verify(notificationClient).sendInAppMessage(captor.capture());
        assertEquals(100L, captor.getValue().getRecipientId(), "通知收件人为商家 user_id");
        assertEquals(200L, captor.getValue().getSenderId());
        assertEquals("FOLLOW", captor.getValue().getType());
        assertEquals("/shop/5", captor.getValue().getLinkUrl());
    }

    @Test
    @DisplayName("关注 — 遗留 sellerId 口径：先解析默认店铺，再按 shopId 记账")
    void shouldResolveDefaultShop_whenLegacySellerIdProvided() {
        when(shopManager.resolveShopId(null, 100L)).thenReturn(5L);
        when(shopManager.getVisibleShop(5L)).thenReturn(shopVO(5L, 100L));
        when(storeFollowStore.follow(200L, 5L)).thenReturn(1L);

        long count = storeFollowManager.follow(200L, null, 100L);

        assertEquals(1L, count);
        verify(storeFollowStore).follow(200L, 5L);
    }

    @Test
    @DisplayName("关注 — 通知失败不阻断关注（只告警）")
    void shouldNotBlockFollow_whenNotifyFails() {
        when(shopManager.resolveShopId(5L, null)).thenReturn(5L);
        when(shopManager.getVisibleShop(5L)).thenReturn(shopVO(5L, 100L));
        when(storeFollowStore.follow(200L, 5L)).thenReturn(4L);
        doThrow(new RuntimeException("notification-service 不可用"))
                .when(notificationClient).sendInAppMessage(any(InAppMessageSendReqCO.class));

        assertEquals(4L, storeFollowManager.follow(200L, 5L, null));
    }

    @Test
    @DisplayName("取关 / 关注状态 / 粉丝数 — 解析后委托关注存储")
    void shouldDelegate_toStore() {
        when(shopManager.resolveShopId(5L, null)).thenReturn(5L);
        when(shopManager.resolveShopId(null, 100L)).thenReturn(5L);

        storeFollowManager.unfollow(200L, 5L, null);
        verify(storeFollowStore).unfollow(200L, 5L);

        when(storeFollowStore.isFollowing(200L, 5L)).thenReturn(true);
        assertTrue(storeFollowManager.isFollowing(200L, 5L, null));

        when(storeFollowStore.followerCount(5L)).thenReturn(7L);
        assertEquals(7L, storeFollowManager.followerCount(null, 100L));
    }
}
