package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品实体 — search-service 精简版，仅查询所需字段。
 * 映射主库 product 表。
 */
@Data
@TableName("product")
public class ProductEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String productId;
    private Long categoryId;
    private String name;
    private String description;
    private String brand;
    private String mainImage;
    private Integer soldCount;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
