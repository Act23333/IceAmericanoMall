package org.icedamericanomall.marketing.domain.model.flash;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;

import java.time.LocalDateTime;

/**
 * 秒杀场次聚合（纯 POJO）—— {@code flash_sale} 表。
 *
 * <p>{@code version} 为乐观锁列：异步统计 soldCount 时以版本号比对，冲突即重试
 * （消费者侧条件更新，见 {@code FlashSaleManager.incrementSoldCountAsync}）。
 */
@Data
@Builder
public class FlashSale {

    private Long id;
    private Long productId;
    private Long skuId;
    private Integer flashPrice;
    private Integer stock;
    private Integer soldCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;

    /** 是否进行中（状态口径，时间窗由列表查询 SQL 过滤）。 */
    public boolean isActive() {
        return FlashSaleStatusEnum.ACTIVE.getCode() == this.status;
    }

    /** 剩余可售库存（DB 口径 = 总库存 - 已售）；用于 Redis 库存惰性初始化。 */
    public int remainingStock() {
        int total = this.stock != null ? this.stock : 0;
        int sold = this.soldCount != null ? this.soldCount : 0;
        return Math.max(total - sold, 0);
    }

    /** DB 口径是否仍可售（异步统计前的兜底守卫）。 */
    public boolean hasSellableStock() {
        int total = this.stock != null ? this.stock : 0;
        int sold = this.soldCount != null ? this.soldCount : 0;
        return sold < total;
    }
}
