package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Stock operation DTO for inter-service Feign calls (trade-service → item-service).
 * Used for batch stock deduction and restoration.
 */
@Data
public class StockOpDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long skuId;

    private Integer quantity;
}
