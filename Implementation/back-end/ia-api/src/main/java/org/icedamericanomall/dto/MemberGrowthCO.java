package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员成长值入账（trade-service 订单完成 → user-service，契约 §4.14.1b I46，V5.8）。
 *
 * <p>幂等锚点 = {@code orderNo}（uk_order_no，一单一次成长，ADR-044）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberGrowthCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 买家 user_id */
    @NotNull
    private Long userId;

    /** 来源订单号（幂等锚点） */
    @NotBlank
    private String orderNo;

    /** 订单实付金额（分，= 本单成长值） */
    @NotNull
    @Positive
    private Integer payAmount;
}
