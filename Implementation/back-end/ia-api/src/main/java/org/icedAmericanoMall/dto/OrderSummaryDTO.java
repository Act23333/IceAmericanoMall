package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Order summary DTO for inter-service Feign calls (trade-service → pay-service).
 */
@Data
public class OrderSummaryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** Total amount in cents. */
    private Integer totalAmount;

    private Integer status;
}
