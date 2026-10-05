package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 拼团下单前置校验入参（trade-service → marketing-service，契约 §4.14.8c I52，V5.14）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyValidateCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 拼团活动 id */
    @NotNull
    private Long activityId;

    /** 团号（空 = 开团校验；非空 = 参团校验：团存在/拼团中/未满/未参过） */
    private String teamNo;

    /** 买家 user_id（参团查重用） */
    @NotNull
    private Long userId;
}
