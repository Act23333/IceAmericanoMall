package org.icedamericanomall.marketing.domain.model.flash;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;

import java.time.LocalDateTime;

/**
 * 秒杀场次聚合（纯 POJO）—— {@code flash_sale} 表。
 *
 * <p>{@code version} 为乐观锁列：异步统计 soldCount 时以版本号比对，冲突即重试
 * （消费者侧条件更新，见 {@code FlashSaleManager.incrementSoldCountAsync}）。
 *
 * <p>V5.7（AD40-AD42）：活动状态由 {@code startTime}/{@code endTime} 时间窗推导
 * （1未开始/2进行中/3已结束），落库 {@code status} 仅为推导结果在写入时刻的快照；
 * 管理端守卫（已结束不可编辑 / 进行中禁删）一律按时间窗实时推导判断，不信任快照。
 */
@Data
@Builder
public class FlashSale {

    private Long id;
    private Long spuId;
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

    /**
     * 按时间窗推导活动状态（V5.7 契约口径）：未开始=1 / 进行中=2 / 已结束=3。
     *
     * @param now 判定时刻
     * @return 状态码，见 {@link FlashSaleStatusEnum}
     */
    public int deriveStatus(LocalDateTime now) {
        if (now.isBefore(this.startTime)) {
            return FlashSaleStatusEnum.PENDING.getCode();
        }
        if (now.isAfter(this.endTime)) {
            return FlashSaleStatusEnum.ENDED.getCode();
        }
        return FlashSaleStatusEnum.ACTIVE.getCode();
    }

    /**
     * 校验时间窗有序（endTime 严格晚于 startTime）—— 聚合不变式（AD40/AD41 字段口径）。
     *
     * @throws BizException 时间窗非法（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public void ensureTimeWindowOrdered() {
        if (this.startTime == null || this.endTime == null || !this.endTime.isAfter(this.startTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
    }

    /** 剩余可售库存（DB 口径 = 总库存 - 已售）；用于 Redis 库存惰性初始化。 */
    public int remainingStock() {
        int total = this.stock != null ? this.stock : 0;
        int sold = this.soldCount != null ? this.soldCount : 0;
        return Math.max(total - sold, 0);
    }

    /**
     * 已售进度百分比（契约 {@code FlashSaleVO.progress_percent}，整数 0–100）。
     *
     * <p>口径：分母为<b>秒杀总库存</b> {@code stock}（{@code flash_sale.stock} 列注释「秒杀库存」= 总量，
     * 剩余量见 {@link #remainingStock()}），分子为已售 {@code soldCount}——与契约样例一致
     * （stock=100 / soldCount=35 → 35）。整数向下取整，异常数据（超卖）夹取到 100。
     *
     * @return 进度百分比；总库存缺失或非正时返回 0（展示字段不抛异常、不阻断列表）
     */
    public int progressPercent() {
        int total = this.stock != null ? this.stock : 0;
        int sold = this.soldCount != null ? this.soldCount : 0;
        if (total <= 0) {
            return 0;
        }
        return Math.min(sold * 100 / total, 100);
    }

    /** DB 口径是否仍可售（异步统计前的兜底守卫）。 */
    public boolean hasSellableStock() {
        int total = this.stock != null ? this.stock : 0;
        int sold = this.soldCount != null ? this.soldCount : 0;
        return sold < total;
    }
}
