package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SKU 实体 — DDD 充血模型 V5.0
 *
 * <pre>
 * 不变量:
 *   1. stock >= 0（库存永远不为负）
 *   2. 扣减库存前必须校验 stock >= quantity
 *   3. 只有 status=1(可售) 才能扣库存
 *   4. stockType=UNLIMITED(2) 的 SKU 不校验库存上限
 * </pre>
 */
@Getter
@TableName("sku")
public class SkuEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==================== 库存类型常量 ====================
    public static final int STOCK_LIMITED   = 1;
    public static final int STOCK_UNLIMITED = 2;
    public static final int STOCK_PRESALE   = 3;

    // ==================== 状态常量 ====================
    public static final int STATUS_ON_SALE  = 1;
    public static final int STATUS_OFF_SALE = 0;

    // ==================== 字段 ====================

    @TableId(type = IdType.AUTO)
    private Long id;
    private String skuId;
    private Long productId;
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

    // ==================== 领域行为 ====================

    /**
     * 扣减库存。
     *
     * @param quantity 扣减数量
     * @throws BizException 库存不足 / SKU 已停售
     */
    public void deductStock(int quantity) {
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "扣减数量必须大于0");
        }
        if (this.status != STATUS_ON_SALE) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "该商品已停售");
        }
        if (this.stockType != STOCK_UNLIMITED) {
            if (this.stock < quantity) {
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT,
                        String.format("库存不足: 需要 %d, 当前 %d", quantity, this.stock));
            }
            this.stock -= quantity;
        }
        this.soldCount = (this.soldCount == null ? 0 : this.soldCount) + quantity;
    }

    /**
     * 恢复库存（取消订单/退款）。
     *
     * @param quantity 恢复数量
     */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "恢复数量必须大于0");
        }
        if (this.stockType != STOCK_UNLIMITED) {
            this.stock += quantity;
        }
        this.soldCount = Math.max(0, (this.soldCount == null ? 0 : this.soldCount) - quantity);
    }

    /** 上架 */
    public void putOnSale() {
        if (this.status == STATUS_ON_SALE) return;
        this.status = STATUS_ON_SALE;
    }

    /** 下架 */
    public void takeOffSale() {
        this.status = STATUS_OFF_SALE;
    }

    // ==================== 查询方法 ====================

    public boolean isOnSale()        { return this.status == STATUS_ON_SALE; }
    public boolean isInStock()       { return this.stockType == STOCK_UNLIMITED || (this.stock != null && this.stock > 0); }
    public boolean hasStock(int qty) { return this.stockType == STOCK_UNLIMITED || (this.stock != null && this.stock >= qty); }

    // ==================== 持久层 setter (仅 MyBatis/Repository 使用) ====================

    public void setId(Long v) { this.id = v; }
    public void setSkuId(String v) { this.skuId = v; }
    public void setProductId(Long v) { this.productId = v; }
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
