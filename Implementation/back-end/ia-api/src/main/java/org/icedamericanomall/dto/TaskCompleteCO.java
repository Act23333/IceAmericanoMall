package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务完成触发（trade/item → marketing-service，契约 §4.14.8c I49，V5.10）。
 *
 * <p>幂等在服务端：同用户同任务同业务日唯一（ADR-047）；调用方只传 userId+taskCode。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompleteCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 user_id */
    @NotNull
    private Long userId;

    /** 任务编码（task_def.code，如 DAILY_ORDER / DAILY_REVIEW） */
    @NotBlank
    private String taskCode;
}
