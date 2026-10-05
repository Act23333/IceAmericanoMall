package org.icedamericanomall.marketing.app.promotion;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.ActivityVO;
import org.icedamericanomall.marketing.domain.enums.PromotionTypeEnum;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动中心查询用例编排（应用层 · 前台，V5.11 M12/M13）。
 *
 * <p>数据源 = {@code promotion} 表：进行中口径与 I44 计价查询一致（时间窗推导
 * {@code start_time ≤ now ≤ end_time}，不信任落库 {@code status} 快照，ADR-043）。
 * M12 为全店铺聚合列表（按 {@code start_time} 降序，最新活动优先）；类型过滤在应用层完成
 * （{@code FULL_REDUCE}/1、{@code LIMIT_DISCOUNT}/2；{@code NEW_USER} 暂无 promotion 数据源
 * → 契约定稿为空列表，Phase 2 接新人礼活动）。
 *
 * <p>M13 详情不按状态过滤：进行中/未开始/已结束均可查；不存在 → {@code NO_RESOURCE_FOUND(1103)}。
 */
@Component
@RequiredArgsConstructor
public class ActivityManager {

    /** M12 契约中的新人礼类型：promotion 表暂无数据源，固定返回空列表（Phase 2）。 */
    private static final String NEW_USER_CONTRACT_NAME = "NEW_USER";

    private final PromotionRepository promotionRepository;
    private final MarketingConverter marketingConverter;

    /**
     * 活动中心列表（M12，匿名可访）：全店铺进行中活动，可选按类型过滤。
     *
     * @param type 契约类型字符串（{@code FULL_REDUCE} / {@code LIMIT_DISCOUNT} / {@code NEW_USER}）；
     *             null/空白 = 全部进行中
     * @return 活动视图列表（按开始时间降序）；无活动或 NEW_USER 时为空集合
     * @throws BizException type 非 contract 定义的三个值之一（{@link ErrorCode#PARAM_ERROR}，400）
     */
    public List<ActivityVO> listActiveActivities(String type) {
        if (type == null || type.isBlank()) {
            return marketingConverter.toActivityVOList(promotionRepository.findAllActive(LocalDateTime.now()));
        }
        if (NEW_USER_CONTRACT_NAME.equals(type)) {
            return List.of();
        }
        PromotionTypeEnum typeEnum = PromotionTypeEnum.ofContractName(type);
        if (typeEnum == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "活动类型非法: " + type);
        }
        List<Promotion> promotions = promotionRepository.findAllActive(LocalDateTime.now());
        return marketingConverter.toActivityVOList(
                promotions.stream().filter(p -> p.getType() != null && typeEnum.getCode() == p.getType()).toList());
    }

    /**
     * 活动详情（M13，匿名可访）：按 promotion id 查询，不限活动状态。
     *
     * @param id 活动主键（promotion 表 id 即业务标识，V5.8 AD49 口径）
     * @return 活动视图对象
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404/1103）
     */
    public ActivityVO getActivityDetail(Long id) {
        Promotion promotion = promotionRepository.findById(id);
        if (promotion == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "活动不存在");
        }
        return marketingConverter.toActivityVO(promotion);
    }
}
