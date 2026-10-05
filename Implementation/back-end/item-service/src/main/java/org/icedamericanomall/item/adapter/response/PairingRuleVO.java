package org.icedamericanomall.item.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搭配规则视图（AD61/AD62/AD63 管理端出参，契约 §4.13.8f）。
 *
 * <p>{@code id} 为 AD63 编辑寻址口径（{@code PUT /rule/{id}}）；不含 version/cipher/hash
 * 等技术字段（09-Constraints VO 约束）。
 */
@Data
public class PairingRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 技术主键（AD63 寻址口径）。 */
    private Long id;

    /** 源类目 ID。 */
    private Long srcCategoryId;

    /** 目标类目 ID。 */
    private Long dstCategoryId;

    /** 排序权重。 */
    private Integer weight;

    /** 备注。 */
    private String remark;

    /** 状态（1=启用 0=停用）。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
