package org.icedamericanomall.user.domain.model.favorite;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品收藏（聚合根，纯 POJO）。
 *
 * <p>业务键 = {@code userId + productId}，<b>去重语义</b>：同一用户对同一商品至多一条收藏记录
 * （新增由仓储的「存在即跳过」写入保证，取消按该业务键删除），聚合间不持有对象引用、
 * 只以商品 ID 引用商品聚合。
 */
@Data
@Builder
public class Favorite {

    /** 技术主键（收藏业务键 = userId + productId，范围操作均在 WHERE 中带 userId 兜底） */
    private Long id;

    /** 所属用户 ID（跨服务 user_id 语义，技术主键） */
    private Long userId;

    /** 收藏的商品 ID（跨聚合仅以 ID 引用，不持有商品对象） */
    private Long productId;

    /** 收藏时间 */
    private LocalDateTime createTime;

    /**
     * 新建一条收藏（业务键 {userId, productId}）。
     *
     * @param userId    所属用户
     * @param productId 商品 ID
     * @return 待持久化的收藏聚合
     */
    public static Favorite of(Long userId, Long productId) {
        return Favorite.builder()
                .userId(userId)
                .productId(productId)
                .build();
    }
}
