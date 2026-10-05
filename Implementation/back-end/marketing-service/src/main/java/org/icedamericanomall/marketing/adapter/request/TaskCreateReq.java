package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务定义创建请求（适配层入参）—— 平台后台 {@code POST /api/admin/task}（V5.13 AD52）。
 *
 * <p>单字段合法性由 JSR-303 绑定期校验（非法 → {@code PARAM_ERROR(1101)} / 400）；
 * {@code code} 为业务唯一键（{@code uk_code}，调用方锚定），重复在应用层先查后插 +
 * 唯一键兜底统一回落 400；{@code type} 的 Phase 1 值域（仅 1=每日）为业务规则，
 * 由聚合 {@code TaskDef.ensureTypeSupported} 守护（缺省 1，Phase 2 扩展位）。
 */
@Data
public class TaskCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务编码（必填，≤32，大写字母/数字/下划线；uk_code 业务唯一键）。 */
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "^[A-Z0-9_]{1,32}$")
    private String code;

    /** 任务名称（必填，≤50）。 */
    @NotBlank
    @Size(max = 50)
    private String name;

    /** 任务类型（缺省 1=每日重置；Phase 1 仅支持 1，非 1 → 400；Phase 2 扩展位）。 */
    private Integer type;

    /** 完成奖励积分（必填，&gt;0；调整立即生效，不追溯已发流水）。 */
    @NotNull
    @Positive
    private Integer rewardPoints;

    /** 状态（缺省 1=启用；0=停用）。 */
    @Min(0)
    @Max(1)
    private Integer status;
}
