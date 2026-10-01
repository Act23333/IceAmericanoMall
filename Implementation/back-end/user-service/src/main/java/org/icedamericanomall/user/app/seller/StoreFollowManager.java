package org.icedamericanomall.user.app.seller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.app.notification.NotificationManager;
import org.icedamericanomall.user.app.port.StoreFollowStore;
import org.springframework.stereotype.Component;

/**
 * 店铺关注编排（应用层）—— 关注关系（{@link StoreFollowStore} 端口，Redis 实现）
 * + 关注成功通知店铺主。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreFollowManager {

    /** 通知类型：店铺关注 */
    private static final String NOTIFY_TYPE_FOLLOW = "FOLLOW";

    private static final String NOTIFY_TITLE = "有人关注了你的店铺";

    private final StoreFollowStore storeFollowStore;
    private final NotificationManager notificationManager;

    /** 关注店铺，返回操作后粉丝数；通知失败不阻断关注。 */
    public long follow(Long userId, Long sellerId) {
        long count = storeFollowStore.follow(userId, sellerId);
        try {
            notificationManager.send(sellerId, userId, null, null,
                    NOTIFY_TYPE_FOLLOW, NOTIFY_TITLE, null, "/shop/" + sellerId);
        } catch (Exception ignored) {
            // 关注关系已生效，通知为尽力而为
        }
        log.debug("用户关注店铺: userId={}, sellerId={}, count={}", userId, sellerId, count);
        return count;
    }

    /** 取消关注。 */
    public void unfollow(Long userId, Long sellerId) {
        storeFollowStore.unfollow(userId, sellerId);
    }

    /** 当前用户是否已关注。 */
    public boolean isFollowing(Long userId, Long sellerId) {
        return storeFollowStore.isFollowing(userId, sellerId);
    }

    /** 店铺粉丝数。 */
    public long followerCount(Long sellerId) {
        return storeFollowStore.followerCount(sellerId);
    }
}
