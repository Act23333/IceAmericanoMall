package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.favorite.Favorite;

import java.util.List;

/**
 * 商品收藏聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>业务键 = {@code userId + productId}：新增按「存在即跳过」保证去重，
 * 取消/查询均在条件中携带 userId，不存在按裸 id 的操作。
 */
public interface FavoriteRepository {

    /** 新增收藏（幂等：同一 {userId, productId} 已存在则不重复插入）。 */
    void addIfAbsent(Favorite favorite);

    /** 按 {userId, productId} 取消收藏，返回影响行数。 */
    int removeByUserIdAndProductId(Long userId, Long productId);

    /** 统计用户收藏总数。 */
    long countByUserId(Long userId);

    /** 分页查询用户收藏（按收藏时间倒序）。 */
    List<Favorite> findPageDescByUserId(Long userId, long pageNum, long pageSize);
}
