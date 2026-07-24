package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class ProductEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productId;

    private Long sellerId;

    private Long categoryId;

    private String name;

    private String mainImage;
    private String images;       // V3.5: JSON 多图URL
    private String videoUrl;     // V3.5: 视频URL
    private String attributes;   // V3.5: JSON 规格参数
    private String serviceTags;  // V3.5: JSON 售后标签
    private Integer viewCount;   // V3.5: 浏览次数

    private String description;

    private String brand;

    private Integer soldCount;

    private Integer commentCount;

    private Integer isAd;

    private Integer status;

    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
