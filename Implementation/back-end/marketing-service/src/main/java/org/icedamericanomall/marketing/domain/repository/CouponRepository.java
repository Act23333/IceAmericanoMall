package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.coupon.Coupon;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券模板仓储（领域端口）—— 实现方：{@code infrastructure.persistence.coupon.CouponRepositoryImpl}。
 *
 * <p>定位口径：一律按业务唯一键 {@code coupon_id} 查询；库存扣减走原子条件 SQL
 * （{@code issued_qty &lt; total_qty} 才 +1），不用「先查后写」。
 */
public interface CouponRepository {

    /** 按业务唯一键取券（不存在返回 null）。 */
    Coupon findByCouponId(String couponId);

    /**
     * 按业务唯一键 + 商家取券（不存在返回 null）。
     *
     * <p>店铺券操作口径：非本店券按「不存在」处理，不泄露他人券的存在性（越权防范）。
     */
    Coupon findByCouponIdAndSeller(String couponId, Long sellerId);

    /** 按技术主键取券（不存在返回 null）。 */
    Coupon findById(Long id);

    /** 新增（id 回填）。 */
    Coupon save(Coupon coupon);

    /** 全量更新（状态流转等）。 */
    void update(Coupon coupon);

    /**
     * 限量券原子领取：{@code issued_qty &lt; total_qty} 才 +1。
     *
     * @return true=扣减成功；false=已领完（条件不满足）
     */
    boolean incrementIssuedQtyIfBelowTotal(Long id, int totalQty);

    /** 已领取数 +1（不限量券/秒杀券同步，非关键路径，调用方吞异常）。 */
    void incrementIssuedQty(Long id);

    /** 可领取模板列表：已发布且未到期。 */
    List<Coupon> findActiveTemplates(LocalDateTime now);

    /** 商家店铺券列表（创建时间倒序）。 */
    List<Coupon> findBySeller(Long sellerId);
}
