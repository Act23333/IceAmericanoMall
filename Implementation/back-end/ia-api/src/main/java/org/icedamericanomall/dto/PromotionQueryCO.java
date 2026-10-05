package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 生效促销查询入参（trade-service → marketing-service，契约 §4.14.8b I44，V5.8）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionQueryCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家 user_id（店铺级活动；跨店下单已禁，一单仅触达单商家） */
    @NotNull
    private Long sellerId;
}
