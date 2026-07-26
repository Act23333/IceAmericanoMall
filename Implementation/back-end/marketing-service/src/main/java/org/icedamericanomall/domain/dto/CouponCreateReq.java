package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CouponCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String couponId;
    @NotBlank
    private String name;
    @NotNull
    private Integer type;
    @NotNull
    @Min(1)
    private Integer value;
    @NotNull
    @Min(0)
    private Integer minAmount;
    @NotNull
    @Min(1)
    private Integer totalQty;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private Integer status;
}
