package org.icedamericanomall.marketing.app.promotion;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.adapter.response.PromotionVO;
import org.icedamericanomall.marketing.domain.enums.PromotionStatusEnum;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 促销活动管理端用例族编排（应用层 · 平台后台，V5.8 AD48-AD50，F5 促销引擎 ADR-043）。
 *
 * <p>与 {@code PromotionQueryManager}（I44 内部计价查询）按用例族拆分（ADR-025，
 * 镜像 {@code FlashSaleAdminManager} 先例）。三个用例均为纯本地 DB 操作，
 * 事务边界在本层（{@code rollbackFor} 全量回滚）。
 *
 * <p><b>状态口径</b>：活动状态一律由时间窗实时推导（{@code Promotion.deriveStatus}），
 * 落库 {@code status} 仅是写入时刻快照；已结束禁编 / 进行中禁删守卫不信任快照。
 */
@Component
@RequiredArgsConstructor
public class PromotionAdminManager {

    /** 满折封顶缺省值：0=不封顶（表默认值显式化，避免依赖隐式默认）。 */
    private static final int DEFAULT_MAX_DISCOUNT = 0;

    private final PromotionRepository promotionRepository;
    private final MarketingConverter marketingConverter;

    /**
     * AD48：创建促销活动（时间窗推导状态落库）。
     *
     * <p>整体已过期的时间窗拒绝创建——保证「已结束」状态不可能由创建产生；
     * {@code maxDiscount} 缺省归一为 0（不封顶），满足列 NOT NULL 约束。
     *
     * @param req 创建请求（字段必填与正数校验已在适配层完成）
     * @return 创建后的活动视图（含回填主键与推导状态）
     * @throws BizException 时间窗无序 / 优惠规则非法（类型非法、满折越界）/
     *                      时间窗整体已过期（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public PromotionVO createPromotion(PromotionCreateReq req) {
        Promotion promotion = marketingConverter.toDomain(req);
        promotion.ensureTimeWindowOrdered();
        promotion.ensureDiscountRuleLegal();
        LocalDateTime now = LocalDateTime.now();
        ensureNotEnded(promotion, now, "促销活动时间窗已整体过期，不可创建");
        promotion.setStatus(promotion.deriveStatus(now));
        if (promotion.getMaxDiscount() == null) {
            promotion.setMaxDiscount(DEFAULT_MAX_DISCOUNT);
        }
        promotionRepository.save(promotion);
        return marketingConverter.toVO(promotion);
    }

    /**
     * AD49：编辑促销活动（null-safe 局部更新 + 状态重推导）。
     *
     * @param id  活动主键
     * @param req 编辑请求（字段全可选，仅覆盖传入字段）
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      已结束活动不可编辑 / 合并后时间窗无序 / 优惠规则非法
     *                      （{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePromotion(Long id, PromotionUpdateReq req) {
        Promotion promotion = requirePromotion(id);
        ensureNotEnded(promotion, LocalDateTime.now(), "已结束的促销活动不可编辑");
        if (req.getName() != null && req.getName().isBlank()) {
            // 空白名称归一为 null（= 不更新），防止空串覆盖既有活动名
            req.setName(null);
        }
        marketingConverter.mergePromotionUpdate(req, promotion);
        promotion.ensureTimeWindowOrdered();
        promotion.ensureDiscountRuleLegal();
        promotion.setStatus(promotion.deriveStatus(LocalDateTime.now()));
        promotionRepository.update(promotion);
    }

    /**
     * AD50：删除促销活动（物理删除，表无逻辑删除列）。
     *
     * @param id 活动主键
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      进行中活动禁删（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePromotion(Long id) {
        Promotion promotion = requirePromotion(id);
        if (promotion.deriveStatus(LocalDateTime.now()) == PromotionStatusEnum.ACTIVE.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "进行中的促销活动禁止删除");
        }
        promotionRepository.deleteById(id);
    }

    // ==================== 内部方法 ====================

    /** 按主键取活动，不存在抛 NO_RESOURCE_FOUND（AD49/AD50 契约错误口径 1103）。 */
    private Promotion requirePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id);
        if (promotion == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "促销活动不存在: " + id);
        }
        return promotion;
    }

    /** 已结束守卫（时间窗实时推导，不信任落库 status 快照）。 */
    private void ensureNotEnded(Promotion promotion, LocalDateTime now, String message) {
        if (promotion.deriveStatus(now) == PromotionStatusEnum.ENDED.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }
}
