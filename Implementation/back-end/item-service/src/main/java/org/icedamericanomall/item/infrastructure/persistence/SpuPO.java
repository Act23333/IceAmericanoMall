package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("spu")
public class SpuPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务商品编号（BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] spuId;

    /**
     * 短码（对外寻址别名，{@code VARCHAR(16) NOT NULL} + {@code uk_short_code}；ADR-063 §决策 8）。
     *
     * <p>DB 列<b>直接存 hex 字符串</b>（不是 {@code BINARY}）：短码不是业务键、无 16 字节语义，
     * 且要能被运维直接肉眼核对「纸上那串码在库里哪一行」。
     */
    private String shortCode;

    private Long sellerId;

    private Long categoryId;

    /** 绑定的运费模板 ID（F2 运费模型，V5.6）；null = 未绑定 → 下单回落商家默认模板。 */
    private Long freightTemplateId;

    private String name;

    private String mainImage;
    private String images;       // V3.5: JSON 多图URL
    private String videoUrl;     // V3.5: 视频URL
    private String attributes;   // V3.5: JSON 规格参数
    private String serviceTags;  // V3.5: JSON 售后标签
    private String salesTags;    // V4.0: JSON 销售标签
    private Integer viewCount;   // V3.5: 浏览次数

    private String description;

    private String brand;

    private Integer soldCount;

    /** 在售 SKU 最低价（检索查询计算列 {@code AS min_price}，非表结构字段；ADR-052 检索出口）。 */
    @TableField(exist = false)
    private Integer minPrice;

    private Integer commentCount;

    private Integer isAd;

    private Integer status;

    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
