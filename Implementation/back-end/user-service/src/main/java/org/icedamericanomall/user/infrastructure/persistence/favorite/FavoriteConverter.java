package org.icedamericanomall.user.infrastructure.persistence.favorite;

import org.icedamericanomall.user.domain.model.favorite.Favorite;

/**
 * {@code FavoritePO} ↔ 领域对象 {@link Favorite} 转换器（基础设施层内部，纯静态转换）。
 */
public final class FavoriteConverter {

    private FavoriteConverter() {
    }

    /** PO → 领域对象（输入为 null 时返回 null）。 */
    public static Favorite toDomain(FavoritePO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return Favorite.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .spuId(dataObject.getSpuId())
                .createTime(dataObject.getCreateTime())
                .build();
    }

    /** 领域对象 → PO（输入为 null 时返回 null）。 */
    public static FavoritePO toDataObject(Favorite favorite) {
        if (favorite == null) {
            return null;
        }
        FavoritePO dataObject = new FavoritePO();
        dataObject.setId(favorite.getId());
        dataObject.setUserId(favorite.getUserId());
        dataObject.setSpuId(favorite.getSpuId());
        dataObject.setCreateTime(favorite.getCreateTime());
        return dataObject;
    }
}
