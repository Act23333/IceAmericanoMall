package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务定义编辑请求（适配层入参）—— 平台后台 {@code PUT /api/admin/task/{code}}（V5.13 AD53）。
 *
 * <p>与 {@link TaskCreateReq} 部分字段同口径但全可选：null 字段不更新（null-safe 局部更新，
 * 合并语义见 {@code TaskAdminManager.update}）；校验注解均对 null 放行（{@code @Pattern}/
 * {@code @Positive}/{@code @Size} 对 null 合法），仅在传入非法值时拒绝
 * （非法 → {@code PARAM_ERROR(1101)} / 400）。注意 {@code name} 不用 {@code @NotBlank}——
 * 其对 null 判无效，会破坏「只传 rewardPoints」的 null-safe 契约；非空白语义由
 * {@code @Pattern} 表达（仅传入时生效）。
 * {@code code} 为路径寻址键不可改；{@code rewardPoints} 调整立即生效（影响后续完成发放，
 * 不追溯已发）；{@code status=0} 停用后触发口 I49 立即回落 {@code completed=false}。
 */
@Data
public class TaskUpdateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务名称（可选，传入时不得为空白且 ≤50；null=不更新）。 */
    @Size(max = 50)
    @Pattern(regexp = "^\\s*\\S[\\s\\S]*$", message = "任务名称传入时不得为空白")
    private String name;

    /** 完成奖励积分（可选，&gt;0；调整立即生效，不追溯已发流水）。 */
    @Positive
    private Integer rewardPoints;

    /** 状态（可选，0=停用 / 1=启用）。 */
    @Min(0)
    @Max(1)
    private Integer status;
}
