package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SKU 持久化对象（数据库表 {@code sku}）。
 *
 * <pre>
 * 不变量（由服务层 + 原子 SQL 保证，本类不承载行为）:
 *   1. stock >= 0：扣减走 {@code UPDATE ... WHERE stock >= ?} 单条原子 SQL
 *   2. 只有 status=1(SkuStatusEnum.ON_SALE) 才能扣库存
 *   3. stockType=2(StockTypeEnum.UNLIMITED) 的 SKU 不校验库存，仅统计销量
 * </pre>
 */
@Getter
@TableName("sku")
public class SkuPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务SKU编码（BINARY(16) ↔ byte[]；hex 形态仅在领域层，ADR-050） */
    private byte[] skuId;
    private Long spuId;
    private String spec;
    private Integer price;
    private Integer originalPrice;
    private Integer stock;
    private String image;
    private Integer soldCount;
    private Integer stockType;
    private Boolean isHot;
    private String hotReason;
    private String salesTags;
    private Integer status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 持久层 setter (仅 MyBatis/Repository 使用) ====================

    public void setId(Long v) { this.id = v; }
    public void setSkuId(byte[] v) { this.skuId = v; }
    public void setSpuId(Long v) { this.spuId = v; }
    public void setSpec(String v) { this.spec = v; }
    public void setPrice(Integer v) { this.price = v; }
    public void setOriginalPrice(Integer v) { this.originalPrice = v; }
    public void setStock(Integer v) { this.stock = v; }
    public void setImage(String v) { this.image = v; }
    public void setSoldCount(Integer v) { this.soldCount = v; }
    public void setStockType(Integer v) { this.stockType = v; }
    public void setIsHot(Boolean v) { this.isHot = v; }
    public void setHotReason(String v) { this.hotReason = v; }
    public void setSalesTags(String v) { this.salesTags = v; }
    public void setStatus(Integer v) { this.status = v; }
    public void setVersion(Integer v) { this.version = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
