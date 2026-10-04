package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.favorite.Favorite;

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

    /** 领域模型 → 视图对象。 */
    public static FavoriteVO from(Favorite favorite) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(favorite.getId());
        vo.setUserId(favorite.getUserId());
        vo.setProductId(favorite.getProductId());
        vo.setCreateTime(favorite.getCreateTime());
        return vo;
    }
}
