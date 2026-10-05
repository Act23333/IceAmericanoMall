package org.icedamericanomall.item.domain.model.kg;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 类目搭配规则聚合根（纯 POJO，零框架依赖）—— KG PAIR_WITH 边的<b>唯一真相源</b>
 * （F15 Phase 1 唯一新增 MySQL 表 {@code pairing_rule}，平台级类目粒度，O3/O5 裁决）。
 *
 * <p><b>聚合边界（小聚合原则）</b>：规则是独立小聚合，仅含自身属性；源/目标类目是
 * {@code Category} 聚合，按 ID 引用不持有对象引用，存在性校验上移应用层
 * （{@code KgAdminManager}，悬空 → 1101）。
 *
 * <p><b>双键语义</b>：{@code id} 为技术主键（AD63 契约寻址口径 {@code PUT /rule/{id}}，
 * 管理端治理面按契约定稿使用）；{@code (srcCategoryId, dstCategoryId)} 为业务唯一键
 * （{@code uk_src_dst}，唯一约束防重，并发竞态由 DuplicateKey 回落 400 兜底）。
 *
 * <p><b>启停即时生效语义（US-KG-6）</b>：{@code status} 落库后由投影同步
 * （afterCommit 边摄取，status 写入边文档）传导至邻居查询——I58 缺省仅返回启用边，
 * 停用规则立即从搭配卡消失，无需等待重建。
 */
@Data
@Builder
public class PairingRule {

    /** 启用。 */
    public static final int STATUS_ENABLED = 1;

    /** 停用（邻居查询按 status 过滤，即时生效）。 */
    public static final int STATUS_DISABLED = 0;

    /** 技术主键（AD63 契约寻址口径）。 */
    private Long id;

    /** 源类目 ID（如：手机；业务唯一键左半，uk_src_dst）。 */
    private Long srcCategoryId;

    /** 目标类目 ID（如：手机配件；业务唯一键右半，uk_src_dst）。 */
    private Long dstCategoryId;

    /** 排序权重（越大越靠前；缺省 0）。 */
    private Integer weight;

    /** 备注（可空，≤255）。 */
    private String remark;

    /** 状态（1=启用 0=停用；缺省启用）。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建规则（未落库，{@code id} 由仓储补齐；weight 缺省 0、status 缺省启用）。
     *
     * @param srcCategoryId 源类目 ID（必填，存在性由应用层先行校验）
     * @param dstCategoryId 目标类目 ID（必填，存在性由应用层先行校验）
     * @param weight        排序权重（null → 0）
     * @param remark        备注（可空）
     * @return 新规则聚合（未落库）
     */
    public static PairingRule create(Long srcCategoryId, Long dstCategoryId, Integer weight, String remark) {
        return PairingRule.builder()
                .srcCategoryId(srcCategoryId)
                .dstCategoryId(dstCategoryId)
                .weight(weight == null ? 0 : weight)
                .remark(remark)
                .status(STATUS_ENABLED)
                .build();
    }

    // ==================== 查询 ====================

    /** 是否启用。 */
    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }

    // ==================== 变更 ====================

    /**
     * PATCH 局部更新（null-safe：{@code null} = 本次不改动该字段，AD63 契约语义，
     * 对齐 AD51-53 null-safe 先例）。src/dst 为业务唯一键，<b>不可</b>经编辑变更。
     *
     * @param weight 排序权重（null = 不改动）
     * @param remark 备注（null = 不改动）
     * @param status 状态（null = 不改动；启停即时生效语义见类注释）
     */
    public void applyPatch(Integer weight, String remark, Integer status) {
        if (weight != null) {
            this.weight = weight;
        }
        if (remark != null) {
            this.remark = remark;
        }
        if (status != null) {
            this.status = status;
        }
    }
}
