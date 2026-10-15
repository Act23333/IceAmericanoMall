package org.icedamericanomall.marketing.app.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.CouponFace;
import org.icedamericanomall.marketing.app.port.CouponFaceQuery;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户持券视图装配（应用层用例族，ADR-025）—— 把持券聚合装配为契约 §4.10.1 的
 * {@link UserCouponVO}，并回填券面展示字段 {@code name} / {@code value} / {@code min_amount}。
 *
 * <p><b>为什么单独一类</b>：持券列表（M3/M4）与领取回执（M1/M2）返回同一个 VO —— 装配若各写一处，
 * 同一 VO 必然两处口径分叉（本仓库 {@code FlashSaleViewManager} 即同一取向的先例）。
 *
 * <p><b>回填口径（ADR-057）</b>：
 * <ul>
 *   <li><b>按 ID 批量</b>：整页去重后的券模板 ID 只打一次数据源，禁止逐行查询（N+1）；</li>
 *   <li><b>fail-open</b>：查询异常 / 模板已删除 → 三字段为 {@code null}，<b>券列表照常返回</b>
 *       —— 券面是展示字段，取不到不能让「我的券」整体失败；</li>
 *   <li><b>本地只读</b>：同库（marketing 自有 {@code coupon} 表）直读，不引入 Feign，
 *       故 {@code @Transactional} 的领取路径可安全复用（09-constraints 事务红线）。</li>
 * </ul>
 *
 * <p>领取路径（M1/M2）的券模板聚合已在编排内加载，直接透出三字段（不再多打一次库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCouponViewAssembler {

    private final MarketingConverter marketingConverter;
    private final CouponFaceQuery couponFaceQuery;

    /**
     * 持券 + 券模板聚合 → 视图对象（券面字段直接来自聚合，不额外查询）。
     *
     * @param userCoupon 持券聚合；{@code null} 返回 {@code null}
     * @param coupon     券模板聚合（可 {@code null}：模板已删除时三字段留空）
     * @return 视图对象
     */
    public UserCouponVO assemble(UserCoupon userCoupon, Coupon coupon) {
        if (userCoupon == null) {
            return null;
        }
        UserCouponVO vo = marketingConverter.toVO(userCoupon);
        applyFace(vo, coupon == null ? null : coupon.getName(),
                coupon == null ? null : coupon.getValue(),
                coupon == null ? null : coupon.getMinAmount());
        return vo;
    }

    /**
     * 持券列表 → 视图对象列表（整页只打一次券面查询，无 N+1）。
     *
     * @param userCoupons 持券聚合列表（可空）
     * @return 视图对象列表；空入参返回空列表。券面缺失时三字段为 {@code null}，列表不缩行
     */
    public List<UserCouponVO> assembleList(List<UserCoupon> userCoupons) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return List.of();
        }
        Map<Long, CouponFace> faces = loadFaces(userCoupons);
        return userCoupons.stream().map(userCoupon -> {
            UserCouponVO vo = marketingConverter.toVO(userCoupon);
            CouponFace face = userCoupon.getCouponId() == null ? null : faces.get(userCoupon.getCouponId());
            applyFace(vo, face == null ? null : face.name(),
                    face == null ? null : face.value(),
                    face == null ? null : face.minAmount());
            return vo;
        }).toList();
    }

    // ==================== 内部方法 ====================

    /**
     * 批量取券面 —— 去重后的券模板 ID 一次查询。
     *
     * <p>fail-open：异常返回空 Map（三字段为 null），不向调用方抛。
     */
    private Map<Long, CouponFace> loadFaces(List<UserCoupon> userCoupons) {
        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (couponIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, CouponFace> faces = couponFaceQuery.findByCouponIds(couponIds);
            return faces == null ? Map.of() : faces;
        } catch (Exception e) {
            log.warn("券面字段回填失败，name/value/min_amount 置空（fail-open）: couponIds={}, cause={}",
                    couponIds, e.getMessage());
            return Map.of();
        }
    }

    /** 券面三字段落 VO（缺失一律保持 null，不回落为 0）。 */
    private void applyFace(UserCouponVO vo, String name, Integer value, Integer minAmount) {
        vo.setName(name);
        vo.setValue(value);
        vo.setMinAmount(minAmount);
    }
}
