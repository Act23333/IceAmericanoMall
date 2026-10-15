package org.icedamericanomall.merchant.app.port;

import java.util.Collection;
import java.util.Map;

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

    /**
     * 批量取店铺粉丝数（{@code ShopVO.follower_count} 回填源，与
     * {@code GET /api/shop/follow/count} 同源同口径）。
     *
     * <p><b>为什么必须批量</b>：商家店铺列表一页 N 行，逐店铺往返 Redis 会退化为 N 次
     * 网络往返；实现须用 pipeline（或等价单次批量读）把 N 个 {@code SCARD} 合并为一次往返。
     *
     * <p><b>失败语义</b>：实现不吞异常 —— Redis 不可用时由调用方 fail-open
     * （粉丝数保持 {@code null}，店铺信息照常返回）。
     *
     * @param shopIds 店铺 ID 集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return 店铺 ID → 粉丝数；键不存在即 0 人关注（与 {@code SCARD} 语义一致，不是「未知」）
     */
    Map<Long, Long> followerCounts(Collection<Long> shopIds);
}
