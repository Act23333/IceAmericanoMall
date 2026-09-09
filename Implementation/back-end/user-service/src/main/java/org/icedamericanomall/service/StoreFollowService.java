package org.icedamericanomall.service;

/**
 * 店铺关注领域服务 — V5.0 DDD 合规
 *
 * 关注/取关/查询等操作下沉到此接口，Controller 不再直接操作 Redis。
 */
public interface StoreFollowService {

    /** 关注店铺，返回操作后粉丝数 */
    long follow(Long userId, Long sellerId);

    /** 取消关注 */
    void unfollow(Long userId, Long sellerId);

    /** 当前用户是否已关注 */
    boolean isFollowing(Long userId, Long sellerId);

    /** 店铺粉丝数 */
    long followerCount(Long sellerId);
}
