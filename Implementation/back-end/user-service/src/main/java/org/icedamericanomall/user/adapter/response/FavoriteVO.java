package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.app.dto.SpuBriefView;
import org.icedamericanomall.user.domain.model.favorite.Favorite;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品收藏视图对象（契约 §4.2.6 U20，字段逐字对齐 05-API-Specification）。
 *
 * <p><b>字段来源分两段</b>：
 * <ol>
 *   <li>收藏本体（{@code id} / {@code spu_id} / {@code create_time}）来自 {@link Favorite} 聚合；</li>
 *   <li>商品展示摘要（{@code product_name} / {@code main_image} / {@code price} / {@code status}）
 *       由应用层读模型按 ID 批量回填（{@link SpuBriefView}）——收藏表不存商品快照。</li>
 * </ol>
 *
 * <p><b>回填缺失（商品已删除 / 数据源降级）时四个商品字段保持 {@code null}</b>：契约保留
 * {@code status} 字段正是为了让前端据此渲染「已下架」，故此处既不过滤也不抛异常（fail-open）。
 */
@Data
public class FavoriteVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 收藏记录主键 */
    private Long id;

    /** 所属用户 ID（取登录上下文，不信任前端传参） */
    private Long userId;

    /** 收藏的商品 ID */
    private Long spuId;

    /** 商品名称（读时回填；缺失为 null） */
    private String productName;

    /** 商品主图（读时回填；缺失为 null） */
    private String mainImage;

    /** 商品价格（分；在售 SKU 最低价，缺失为 null） */
    private Integer price;

    /** 商品状态：1=上架，2=下架，3=删除（读时回填；缺失为 null） */
    private Integer status;

    /** 收藏时间 */
    private LocalDateTime createTime;

    /**
     * 领域模型 + 商品展示摘要 → 视图对象。
     *
     * @param favorite 收藏聚合（非空）
     * @param brief    商品展示摘要；商品已删除或回填降级时为 {@code null}，
     *                 此时四个商品字段置空（fail-open，收藏列表照常返回）
     * @return 收藏视图对象
     */
    public static FavoriteVO from(Favorite favorite, SpuBriefView brief) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(favorite.getId());
        vo.setUserId(favorite.getUserId());
        vo.setSpuId(favorite.getSpuId());
        vo.setCreateTime(favorite.getCreateTime());
        if (brief != null) {
            vo.setProductName(brief.productName());
            vo.setMainImage(brief.mainImage());
            vo.setPrice(brief.price());
            vo.setStatus(brief.status());
        }
        return vo;
    }
}
