package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Cart item DTO for inter-service Feign calls (cart-service → trade-service).
 */
@Data
public class CartItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Cart item primary key (for removal after order). */
    private Long cartItemId;

    private Long skuId;

    private Long productId;

    private String productName;

    private String spec;

    private String image;

    /** Price in cents. */
    private Integer price;

    private Integer quantity;
}
