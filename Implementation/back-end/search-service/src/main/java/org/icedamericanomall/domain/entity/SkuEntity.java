package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SKU 实体 — search-service 精简版，仅查询价格和库存。
 * 映射主库 sku 表。
 */
@Data
@TableName("sku")
public class SkuEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skuId;
    private Long productId;
    private String spec;
    private Integer price;
    private Integer stock;
    private Integer status;
    private Integer soldCount;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
