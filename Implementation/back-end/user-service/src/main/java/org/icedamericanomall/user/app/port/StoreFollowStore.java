package org.icedamericanomall.user.app.port;

/**
 * 店铺关注存储（应用层端口，基础设施层用 Redis Set 实现）。
 *
 * <p>Key 布局：{@code shop:followers:{sellerId}}（店铺粉丝）+
 * {@code user:follow:shops:{userId}}（用户关注列表）；仅本服务读写，无跨服务消费者。
 */
public interface StoreFollowStore {

    /** 建立关注关系，返回操作后粉丝数。 */
    long follow(Long userId, Long sellerId);

    /** 取消关注。 */
    void unfollow(Long userId, Long sellerId);

    /** 当前用户是否已关注。 */
    boolean isFollowing(Long userId, Long sellerId);

    /** 店铺粉丝数。 */
    long followerCount(Long sellerId);
}
