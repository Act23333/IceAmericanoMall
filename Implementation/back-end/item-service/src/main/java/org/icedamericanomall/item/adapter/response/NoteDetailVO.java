package org.icedamericanomall.item.adapter.response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 笔记详情视图对象（P30）—— 已发布笔记全量正文 + 图集 + 关联商品摘要卡。
 *
 * <p>{@code liked} 为<b>登录用户视角</b>回填（匿名访问恒 false）；关联商品按发布时的
 * sort 序返回且<b>已过滤非在售</b>（下架/删除商品不出现，详见 NoteManager 装配）。
 */
@Data
public class NoteDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String content;

    /** 图集（≤9 个 URL）。 */
    private List<String> images;

    private Integer likeCount;

    /** 当前登录用户是否已点赞（匿名恒 false）。 */
    private Boolean liked;

    /** 作者 user_id。 */
    private Long authorId;

    private LocalDateTime createTime;

    /** 关联商品摘要卡（sort 序，非在售已过滤；可空列表）。 */
    private List<SpuSummaryVO> spus;

    /**
     * 关联商品摘要卡（内嵌）—— 商品名称/主图/在售 SKU 最低价（分，口径对齐商品列表卡）。
     */
    @Data
    public static class SpuSummaryVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** SPU 业务键（32-hex，{@code spu.spu_id}；ADR-058 §1 / ADR-061 §决策 2）。 */
        private String spuId;

        /** SPU 名 —— 线上键 {@code spu_name}（契约 §1.8④；原 {@code name}）。 */
        private String spuName;

        private String mainImage;

        /** 在售 SKU 最低价（分）；无在售 SKU 时为 null。 */
        private Integer minPrice;
    }
}
