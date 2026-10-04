package org.icedamericanomall.merchant.app.port;

/**
 * 店铺关注存储（应用层端口，基础设施层用 Redis Set 实现）。
 *
 * <p>Key 布局（V5.4 起以 shopId 记账，模块内私有，无跨服务消费者）：
 * {@code shop:followers:{shopId}}（店铺粉丝）+
 * {@code merchant:follow:shops:{userId}}（用户关注列表）。
 *
 * <p>键口径由 {@code sellerId(=商家 user_id)} 改为 {@code shopId} 属于开发期调整，
 * 存量 Redis 数据可弃（无迁移脚本，客户端重新关注即恢复）。
 */
public interface StoreFollowStore {

    /** 建立关注关系，返回操作后粉丝数。 */
    long follow(Long userId, Long shopId);

    /** 取消关注。 */
    void unfollow(Long userId, Long shopId);

    /** 当前用户是否已关注。 */
    boolean isFollowing(Long userId, Long shopId);

    /** 店铺粉丝数。 */
    long followerCount(Long shopId);
}
