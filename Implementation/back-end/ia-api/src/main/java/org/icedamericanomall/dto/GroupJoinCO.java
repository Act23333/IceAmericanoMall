package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 拼团参团/开团入参（trade-service 支付成功后 → marketing-service，契约 §4.14.8c I53，V5.14）。
 *
 * <p>幂等锚点 = {@code orderNo}（uk_order_no，一单一团一次，ADR-049）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupJoinCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 买家 user_id（开团时 = 团长） */
    @NotNull
    private Long userId;

    /** 拼团订单号（幂等锚点） */
    @NotBlank
    private String orderNo;

    /** 拼团活动 id */
    @NotNull
    private Long activityId;

    /** 团号（空 = 开团；非空 = 参团） */
    private String teamNo;
}
