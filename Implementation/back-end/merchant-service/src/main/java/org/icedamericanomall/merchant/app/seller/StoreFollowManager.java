package org.icedamericanomall.merchant.app.seller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.springframework.stereotype.Component;

/**
 * 店铺关注编排（应用层）—— 关注关系（{@link StoreFollowStore} 端口，Redis 实现）
 * + 关注成功通知店铺主（站内信经 ia-api 的 {@code NotificationClient} 稳定契约
 * 调 notification-service 落库）。
 *
 * <p>V5.4（ADR-019）起统一以 {@code shopId} 记账：遗留 {@code sellerId} 入参先解析为
 * 默认店铺 ID；关注对象仅限正常状态店铺。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreFollowManager {

    /** 通知类型：店铺关注 */
    private static final String NOTIFY_TYPE_FOLLOW = "FOLLOW";

    private static final String NOTIFY_TITLE = "有人关注了你的店铺";

    private final StoreFollowStore storeFollowStore;
    private final ShopManager shopManager;
    private final NotificationClient notificationClient;

    /**
     * 关注店铺（{@code shopId} 与遗留 {@code sellerId} 二选一），返回操作后粉丝数；
     * 通知失败不阻断关注。
     */
    public long follow(Long userId, Long shopId, Long sellerId) {
        ShopVO shop = shopManager.getVisibleShop(shopManager.resolveShopId(shopId, sellerId));
        long count = storeFollowStore.follow(userId, shop.getShopId());
        notifyOwner(userId, shop);
        log.debug("[shop][follow][ok] userId={}, shopId={}, count={}", userId, shop.getShopId(), count);
        return count;
    }

    /** 取消关注。 */
    public void unfollow(Long userId, Long shopId, Long sellerId) {
        storeFollowStore.unfollow(userId, shopManager.resolveShopId(shopId, sellerId));
    }

    /** 当前用户是否已关注。 */
    public boolean isFollowing(Long userId, Long shopId, Long sellerId) {
        return storeFollowStore.isFollowing(userId, shopManager.resolveShopId(shopId, sellerId));
    }

    /** 店铺粉丝数。 */
    public long followerCount(Long shopId, Long sellerId) {
        return storeFollowStore.followerCount(shopManager.resolveShopId(shopId, sellerId));
    }

    /** 关注成功通知店铺主（尽力而为，失败只告警）。 */
    private void notifyOwner(Long userId, ShopVO shop) {
        try {
            InAppMessageSendReqCO message = new InAppMessageSendReqCO();
            message.setRecipientId(shop.getSellerId());
            message.setSenderId(userId);
            message.setType(NOTIFY_TYPE_FOLLOW);
            message.setTitle(NOTIFY_TITLE);
            message.setLinkUrl("/shop/" + shop.getShopId());
            notificationClient.sendInAppMessage(message);
        } catch (Exception e) {
            log.warn("[shop][follow][notify-fail] userId={}, shopId={}, sellerId={}",
                    userId, shop.getShopId(), shop.getSellerId(), e);
        }
    }
}
