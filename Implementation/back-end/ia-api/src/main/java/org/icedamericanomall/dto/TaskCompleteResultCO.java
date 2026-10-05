package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务完成结果（I49 出参，V5.10）：首次完成=true+实发积分；重复/停用/不存在=false+0。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompleteResultCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否本次完成（true=首次并发胜出并发奖） */
    private boolean completed;

    /** 实发奖励积分（未完成为 0） */
    private int rewardPoints;
}
