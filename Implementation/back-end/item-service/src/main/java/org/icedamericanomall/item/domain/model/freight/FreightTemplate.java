package org.icedamericanomall.item.domain.model.freight;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.domain.enums.FreightChargeModeEnum;
import org.icedamericanomall.item.domain.enums.FreightTemplateStatusEnum;

import java.time.LocalDateTime;
import java.util.Objects;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * 运费模板聚合根（纯 POJO，零框架依赖，F2 运费模型）。
 *
 * <pre>
 * 不变量（由聚合方法 + 领域服务 FreightTemplateAssessor 保证）:
 *   1. (seller_id, template_name) 唯一 —— 同商家内模板名不可重复；
 *   2. fee >= 0；free_threshold >= 0（0 = 不参与满额包邮）；
 *   3. 停用（status=0）模板不参与下单计价；
 *   4. 归属校验：模板不存在与"属于他人"统一拒绝为 3041（不区分，避免探知他人模板是否存在）。
 * </pre>
 *
 * <p><b>seller_id 口径</b>：商家 {@code user_id}（与 {@code spu.seller_id} 同口径，ADR-019 Phase A），
 * 不是 {@code seller.id}。
 */
@Data
@Builder
public class FreightTemplate {

    private Long id;

    /** 模板业务编号（UUID，32 位）。 */
    private String templateNo;

    /** 商家 ID（= 商家 user_id）。 */
    private Long sellerId;

    /** 模板名称（同商家内唯一）。 */
    private String templateName;

    /** 计费方式：1-固定运费（Phase 1 唯一值）。 */
    private Integer chargeMode;

    /** 固定运费（分，≥0）。 */
    private Integer fee;

    /** 满额包邮阈值（分）；0 = 不参与满额包邮。 */
    private Integer freeThreshold;

    /** 状态：1-启用，0-停用（{@link FreightTemplateStatusEnum}）。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 新建运费模板（未落库，{@code id} 由仓储补齐）。
     *
     * <p>缺省值归一（与历史实现一致）：
     * <ul>
     *   <li>{@code chargeMode}：null/非法 → {@link FreightChargeModeEnum#FIXED}（Phase 1 唯一值）</li>
     *   <li>{@code freeThreshold}：null → 0（不参与满额包邮）</li>
     *   <li>{@code status}：null/非法 → {@link FreightTemplateStatusEnum#ENABLED}</li>
     * </ul>
     *
     * <p>归一逻辑由领域承担（原先散落在适配层请求对象的 {@code normalizedXxx()} 方法中，
     * 属"业务规则污染入参对象"的反模式）。
     */
    public static FreightTemplate create(Long sellerId, String templateName, Integer chargeMode,
                                         Integer fee, Integer freeThreshold, Integer status) {
        return FreightTemplate.builder()
                .templateNo(IdGenerator.nextId())
                .sellerId(sellerId)
                .templateName(templateName)
                .chargeMode(normalizeChargeMode(chargeMode))
                .fee(fee)
                .freeThreshold(freeThreshold == null ? 0 : freeThreshold)
                .status(normalizeStatus(status))
                .build();
    }

    // ==================== 查询 ====================

    /** 是否归属指定商家。 */
    public boolean isOwnedBy(Long sellerId) {
        return Objects.equals(this.sellerId, sellerId);
    }

    /** 是否启用（启用模板才参与下单计价）。 */
    public boolean isEnabled() {
        return status != null && status == FreightTemplateStatusEnum.ENABLED.getCode();
    }

    /**
     * 本次编辑是否构成「改名」（用于触发同商家模板名唯一性校验）。
     *
     * @param newName 请求携带的新名称（null = 不改名）
     */
    public boolean isRenamingTo(String newName) {
        return newName != null && !newName.equals(this.templateName);
    }

    // ==================== 业务断言 ====================

    /**
     * 断言归属当前商家。
     *
     * <p>不区分「模板不存在」与「模板属于他人」，统一 {@code FREIGHT_TEMPLATE_NOT_FOUND(3041)}，
     * 避免探知他人模板是否存在。
     *
     * @throws BizException 非本商家模板（{@code FREIGHT_TEMPLATE_NOT_FOUND}）
     */
    public void assertOwnedBy(Long sellerId) {
        if (!isOwnedBy(sellerId)) {
            throw new BizException(ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND, "运费模板不存在");
        }
    }

    // ==================== 变更 ====================

    /**
     * PATCH 局部更新（{@code null} = 本次不改动该字段）。
     *
     * <p>归一规则与新建一致（chargeMode 回落固定运费、status 回落启用）；
     * 改名唯一性校验由领域服务在调用本方法前完成。
     */
    public void applyPatch(String templateName, Integer chargeMode, Integer fee,
                           Integer freeThreshold, Integer status) {
        if (templateName != null) {
            this.templateName = templateName;
        }
        if (chargeMode != null) {
            this.chargeMode = normalizeChargeMode(chargeMode);
        }
        if (fee != null) {
            this.fee = fee;
        }
        if (freeThreshold != null) {
            this.freeThreshold = freeThreshold;
        }
        if (status != null) {
            this.status = normalizeStatus(status);
        }
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 私有归一 ====================

    /** 计费方式归一：null 或非法一律回落固定运费（Phase 1 唯一值）。 */
    private static Integer normalizeChargeMode(Integer chargeMode) {
        FreightChargeModeEnum mode = FreightChargeModeEnum.of(chargeMode);
        return mode == null ? FreightChargeModeEnum.FIXED.getCode() : mode.getCode();
    }

    /** 状态归一：null 或非法一律回落启用。 */
    private static Integer normalizeStatus(Integer status) {
        FreightTemplateStatusEnum statusEnum = FreightTemplateStatusEnum.of(status);
        return statusEnum == null ? FreightTemplateStatusEnum.ENABLED.getCode() : statusEnum.getCode();
    }
}
