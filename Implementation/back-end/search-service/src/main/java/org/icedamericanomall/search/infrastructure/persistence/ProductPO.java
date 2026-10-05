package org.icedamericanomall.search.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品 PO — search-service 精简版，仅查询所需字段。
 * 映射主库 product 表。
 */
@Data
@TableName("product")
public class ProductPO implements Serializable {

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

    /** 上架时间（SE1 newest 排序与 I47 publishTime 回填；映射主库 product.publish_time 列）。 */
    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
