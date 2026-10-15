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
 * {@link UserCouponVO}，并回填<b>业务券号</b>与券面展示字段 {@code name} / {@code value} /
 * {@code min_amount}。
 *
 * <p><b>为什么单独一类</b>：持券列表（M3/M4）与领取回执（M1/M2）返回同一个 VO —— 装配若各写一处，
 * 同一 VO 必然两处口径分叉（本仓库 {@code FlashSaleViewManager} 即同一取向的先例）。
 *
 * <p><b>回填口径（ADR-057）</b>：
 * <ul>
 *   <li><b>按 ID 批量</b>：整页去重后的券模板 ID 只打一次数据源，禁止逐行查询（N+1）；</li>
 *   <li><b>fail-open</b>：查询异常 / 模板已删除 → 字段为 {@code null}，<b>券列表照常返回</b>
 *       —— 券面是展示字段，取不到不能让「我的券」整体失败；</li>
 *   <li><b>本地只读</b>：同库（marketing 自有 {@code coupon} 表）直读，不引入 Feign，
 *       故 {@code @Transactional} 的领取路径可安全复用（09-constraints 事务红线）。</li>
 * </ul>
 *
 * <p><b>业务券号来源（ADR-002 + ADR-050）</b>：{@code UserCouponVO.couponId} 是<b>业务券号</b>
 * （{@code coupon.coupon_id} 的 32-hex），<b>不是</b> {@code UserCoupon.couponId}（技术外键）——
 * 故本类不从持券聚合的同名属性投影，而是取券模板侧的值：
 * 列表路径取读模型 {@link CouponFace#couponId()}；领取回执路径取已加载的券模板聚合
 * {@link Coupon#getCouponId()}（{@code CouponRepositoryImpl.toDomain} 已解出 hex）。取不到一律
 * {@code null}（与券面三字段同口径），持券行照常返回、不缩行。
 *
 * <p>领取路径（M1/M2）的券模板聚合已在编排内加载，直接透出字段（不再多打一次库）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCouponViewAssembler {

    private final MarketingConverter marketingConverter;
    private final CouponFaceQuery couponFaceQuery;

    /**
     * 持券 + 券模板聚合 → 视图对象（业务券号与券面字段直接来自聚合，不额外查询）。
     *
     * @param userCoupon 持券聚合；{@code null} 返回 {@code null}
     * @param coupon     券模板聚合（可 {@code null}：模板已删除时业务券号与券面字段留空）
     * @return 视图对象
     */
    public UserCouponVO assemble(UserCoupon userCoupon, Coupon coupon) {
        if (userCoupon == null) {
            return null;
        }
        UserCouponVO vo = marketingConverter.toVO(userCoupon);
        applyCouponTemplateFields(vo, coupon == null ? null : coupon.getCouponId(),
                coupon == null ? null : coupon.getName(),
                coupon == null ? null : coupon.getValue(),
                coupon == null ? null : coupon.getMinAmount());
        return vo;
    }

    /**
     * 持券列表 → 视图对象列表（整页只打一次券面查询，无 N+1）。
     *
     * @param userCoupons 持券聚合列表（可空）
     * @return 视图对象列表；空入参返回空列表。券模板缺失（含查询异常）时业务券号与券面字段为
     *         {@code null}，列表不缩行
     */
    public List<UserCouponVO> assembleList(List<UserCoupon> userCoupons) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return List.of();
        }
        Map<Long, CouponFace> faces = loadFaces(userCoupons);
        return userCoupons.stream().map(userCoupon -> {
            UserCouponVO vo = marketingConverter.toVO(userCoupon);
            CouponFace face = userCoupon.getCouponId() == null ? null : faces.get(userCoupon.getCouponId());
            applyCouponTemplateFields(vo, face == null ? null : face.couponId(),
                    face == null ? null : face.name(),
                    face == null ? null : face.value(),
                    face == null ? null : face.minAmount());
            return vo;
        }).toList();
    }

    // ==================== 内部方法 ====================

    /**
     * 批量取券面（含业务券号）—— 去重后的券模板 ID 一次查询。
     *
     * <p>fail-open：异常返回空 Map（字段为 null），不向调用方抛。
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
            log.warn("券模板字段回填失败，coupon_id/name/value/min_amount 置空（fail-open）: couponIds={}, cause={}",
                    couponIds, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 券模板相关字段落 VO：业务券号 {@code couponId} + 券面三字段。
     *
     * <p>缺失一律保持 {@code null}（不回落为 0、不回落为技术主键），
     * 保证「取不到」与「值恰为 0」在报文上可区分。
     */
    private void applyCouponTemplateFields(UserCouponVO vo, String couponId,
                                           String name, Integer value, Integer minAmount) {
        vo.setCouponId(couponId);
        vo.setName(name);
        vo.setValue(value);
        vo.setMinAmount(minAmount);
    }
}
