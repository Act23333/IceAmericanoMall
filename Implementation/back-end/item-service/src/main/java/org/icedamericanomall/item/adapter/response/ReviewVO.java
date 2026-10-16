package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品评价视图对象 — V5.0 增强
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：{@code spuId} / {@code skuId} 均为
 * <b>业务键</b> 32-hex {@code String}（{@code spu.spu_id} / {@code sku.sku_id}）；
 * {@code id} / {@code userId} 是行主键（渲染 key / 作者标识），按 §1.8 例外条款保持现状。
 * 业务键由应用层 {@code ReviewManager} 用同库只读查询批量回填（禁 N+1），
 * 与 {@code SpuVO.spuId} / {@code SkuVO.spuId} 同型同义 —— 保证「评价回链商品页」在类型层成立。
 */
@Data
public class ReviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    /** SPU 业务键（32-hex，{@code spu.spu_id}）。 */
    private String spuId;
    private String orderNo;
    /** SKU 业务键（32-hex，{@code sku.sku_id}）；未关联 SKU 时为 null。 */
    private String skuId;
    private Integer rating;
    private String content;
    private String images;
    /** V5.0: 评价标签 ["质量好","物流快"] */
    private String tags;

    // V5.0: 商家回复
    private String reply;
    private LocalDateTime replyTime;

    // V5.0: 追评
    private String appendContent;
    private String appendMediaUrls;
    private LocalDateTime appendTime;

    // V5.0: 点赞
    private Integer likeCount;
    private Boolean likedByMe;

    private LocalDateTime createTime;
}
