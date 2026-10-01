package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.infrastructure.persistence.favorite.FavoriteDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品收藏视图对象。
 */
@Data
public class FavoriteVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createTime;

    /** 持久化对象 → 视图对象。 */
    public static FavoriteVO from(FavoriteDO dataObject) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(dataObject.getId());
        vo.setUserId(dataObject.getUserId());
        vo.setProductId(dataObject.getProductId());
        vo.setCreateTime(dataObject.getCreateTime());
        return vo;
    }
}
