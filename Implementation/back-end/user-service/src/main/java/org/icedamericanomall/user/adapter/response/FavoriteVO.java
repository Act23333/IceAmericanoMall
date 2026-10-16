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
 *   <li>收藏本体（{@code id} / {@code spu_id} / {@code create_time}）来自 {@link Favorite} 聚合
 *       （其中 {@code spu_id} 的业务键形态由读模型同批带出，见下）；</li>
 *   <li>商品展示摘要（{@code spu_name} / {@code main_image} / {@code price} / {@code status}）
 *       由应用层读模型按业务键批量回填（{@link SpuBriefView}）——收藏表不存商品快照。</li>
 * </ol>
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：{@code spuId} 为 <b>SPU 业务键</b>
 * 32-hex {@code String}（可直接用于回链商品页）；{@code id} 仍是 {@code favorite.id}
 * <b>资源行主键</b>（§1.8 例外条款，保持现状）。
 *
 * <p><b>回填缺失（商品已删除 / 数据源降级）时商品字段保持 {@code null}</b>：契约保留
 * {@code status} 字段正是为了让前端据此渲染「已下架」，故此处既不过滤也不抛异常（fail-open）。
 */
@Data
public class FavoriteVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 收藏记录主键（资源行主键）。 */
    private Long id;

    /** 所属用户 ID（取登录上下文，不信任前端传参） */
    private Long userId;

    /** 收藏的商品业务键（{@code spu.spu_id} 的 32-hex 形态）。 */
    private String spuId;

    /** 商品名称（读时回填；缺失为 null） */
    private String spuName;

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
        // spu_id 出业务键：唯一来源是同一条读模型 SQL（spu.spu_id）；回填降级时置空 ——
        // 按 ADR-061 §决策 3「不得回落技术键」（收藏行自身的 sku/spu_id 是技术外键）
        vo.setSpuId(brief == null ? null : brief.spuBusinessId());
        vo.setCreateTime(favorite.getCreateTime());
        if (brief != null) {
            vo.setSpuName(brief.productName());
            vo.setMainImage(brief.mainImage());
            vo.setPrice(brief.price());
            vo.setStatus(brief.status());
        }
        return vo;
    }
}
