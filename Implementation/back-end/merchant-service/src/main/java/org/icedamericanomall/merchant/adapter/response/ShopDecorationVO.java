package org.icedamericanomall.merchant.adapter.response;

import lombok.Data;
import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 店铺装修视图对象（S1c 出参 / SH1 {@code ShopVO.decoration} 增量，ADR-048）。
 *
 * <p>字段口径（S1c）：未装修 → 公告为空值、两个列表为空数组；
 * 作为 SH1 内嵌对象时未装修 → 整体 {@code null}（由装配方决定）。
 */
@Data
public class ShopDecorationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 店铺公告 */
    private String announcement;

    /** 轮播图 URL（≤5） */
    private List<String> banners;

    /** 推荐商品位（product.id，≤8，去重保序；展示端过滤失效 / 非在售 id） */
    private List<Long> hotProductIds;

    /** 领域模型 → 视图对象。 */
    public static ShopDecorationVO from(ShopDecoration decoration) {
        ShopDecorationVO vo = new ShopDecorationVO();
        vo.setAnnouncement(decoration.getAnnouncement());
        vo.setBanners(copyOf(decoration.getBanners()));
        vo.setHotProductIds(copyOf(decoration.getHotProductIds()));
        return vo;
    }

    /** 未装修空对象（公告空值 + 空数组，S1c 口径）。 */
    public static ShopDecorationVO empty() {
        ShopDecorationVO vo = new ShopDecorationVO();
        vo.setBanners(List.of());
        vo.setHotProductIds(List.of());
        return vo;
    }

    private static <T> List<T> copyOf(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
