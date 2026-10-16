package org.icedamericanomall.marketing.app.groupbuy;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.domain.enums.GroupBuyActivityStatusEnum;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.repository.GroupBuyActivityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 拼团活动管理端用例族编排（应用层 · 平台后台，V5.14 AD54-AD56，F13 拼团 ADR-049）。
 *
 * <p>与 {@code GroupBuyParticipationManager}（C 端浏览 + I52/I53 参团联动）按用例族拆分
 * （ADR-025，镜像 {@code PromotionAdminManager} 先例）。三个用例均为纯本地 DB 操作，
 * 事务边界在本层（{@code rollbackFor} 全量回滚）。
 *
 * <p><b>状态口径</b>：活动状态一律由时间窗实时推导（{@code GroupBuyActivity.deriveStatus}），
 * 落库 {@code status} 仅是写入时刻快照；已结束禁编 / 进行中禁删守卫不信任快照。
 */
@Component
@RequiredArgsConstructor
public class GroupBuyAdminManager {

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final MarketingConverter marketingConverter;
    private final GroupBuyViewManager groupBuyViewManager;

    /**
     * AD54：创建拼团活动（时间窗推导状态落库）。
     *
     * <p>整体已过期的时间窗拒绝创建——保证「已结束」状态不可能由创建产生；
     * 拼团字段不变式（拼团价&gt;0 / 成团人数≥2 / 时限≥1 小时）由聚合守护。
     *
     * @param req 创建请求（字段必填与正数校验已在适配层完成）
     * @return 创建后的活动视图（含回填主键与推导状态）
     * @throws BizException 时间窗无序 / 字段越界 / 时间窗整体已过期
     *                      （{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyActivityVO createActivity(GroupBuyCreateReq req) {
        GroupBuyActivity activity = marketingConverter.toDomain(req);
        applyResolvedKeys(activity, req.getSpuId(), req.getSkuId());
        activity.ensureTimeWindowOrdered();
        activity.ensureFieldsLegal();
        LocalDateTime now = LocalDateTime.now();
        ensureNotEnded(activity, now, "拼团活动时间窗已整体过期，不可创建");
        activity.setStatus(activity.deriveStatus(now));
        groupBuyActivityRepository.save(activity);
        // 回执与 C 端列表同一装配口径（业务键 + SPU 名；fail-open）
        return groupBuyViewManager.toView(activity);
    }

    /**
     * AD55：编辑拼团活动（null-safe 局部更新 + 状态重推导）。
     *
     * @param id  活动主键
     * @param req 编辑请求（字段全可选，仅覆盖传入字段）
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      已结束活动不可编辑 / 合并后时间窗无序 / 字段越界
     *                      （{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(Long id, GroupBuyUpdateReq req) {
        GroupBuyActivity activity = requireActivity(id);
        ensureNotEnded(activity, LocalDateTime.now(), "已结束的拼团活动不可编辑");
        if (req.getName() != null && req.getName().isBlank()) {
            // 空白名称归一为 null（= 不更新），防止空串覆盖既有活动名
            req.setName(null);
        }
        marketingConverter.mergeGroupBuyUpdate(req, activity);
        // 仅当请求真的传了键才覆盖（null = 不更新）；同名异义由转换器 ignore + 此处显式赋值
        if (req.getSkuId() != null || req.getSpuId() != null) {
            applyResolvedKeys(activity, req.getSpuId(), req.getSkuId());
        }
        activity.ensureTimeWindowOrdered();
        activity.ensureFieldsLegal();
        activity.setStatus(activity.deriveStatus(LocalDateTime.now()));
        groupBuyActivityRepository.update(activity);
    }

    /**
     * AD56：删除拼团活动（物理删除，表无逻辑删除列）。
     *
     * @param id 活动主键
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      进行中活动禁删（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long id) {
        GroupBuyActivity activity = requireActivity(id);
        if (activity.deriveStatus(LocalDateTime.now()) == GroupBuyActivityStatusEnum.ACTIVE.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "进行中的拼团活动禁止删除");
        }
        groupBuyActivityRepository.deleteById(id);
    }

    // ==================== 内部方法 ====================

    /**
     * SPU/SKU <b>业务键</b> → 技术键并写入聚合（ADR-061 §决策 2 + A 案）。
     *
     * <p>解析走 {@code sku.sku_id} 唯一索引（本地只读，无 Feign，事务安全）；解析不到即
     * {@code PARAM_ERROR} —— 写路径 fail-fast，不落「指向不存在 SKU」的活动。
     * {@code spuId} 以 <b>SKU 行所属 SPU</b> 为准（入参 SPU 仅作一致性校验）。
     *
     * @throws BizException 业务键格式非法 / SKU 不存在 / SPU 与 SKU 不匹配
     */
    private void applyResolvedKeys(GroupBuyActivity activity, String spuBusinessId, String skuBusinessId) {
        var brief = groupBuyViewManager.findBySkuBusinessId(skuBusinessId);
        if (brief == null || brief.skuId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SKU 不存在（sku_id 业务键无效）");
        }
        if (spuBusinessId != null && brief.spuBusinessId() != null
                && !spuBusinessId.equals(brief.spuBusinessId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPU 与 SKU 不匹配");
        }
        activity.setSkuId(brief.skuId());
        activity.setSpuId(brief.spuId());
    }

    /** 按主键取活动，不存在抛 NO_RESOURCE_FOUND（AD55/AD56 契约错误口径 1103）。 */
    private GroupBuyActivity requireActivity(Long id) {
        GroupBuyActivity activity = groupBuyActivityRepository.findById(id);
        if (activity == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "拼团活动不存在: " + id);
        }
        return activity;
    }

    /** 已结束守卫（时间窗实时推导，不信任落库 status 快照）。 */
    private void ensureNotEnded(GroupBuyActivity activity, LocalDateTime now, String message) {
        if (activity.deriveStatus(now) == GroupBuyActivityStatusEnum.ENDED.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }
}
