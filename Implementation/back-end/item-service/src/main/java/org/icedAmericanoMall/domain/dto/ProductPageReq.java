package org.icedAmericanoMall.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ProductPageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long categoryId;
    private String keyword;
    private String sort;      // "sales" or "price"
    private String order;     // "asc" or "desc"
    private Integer page = 1;
    private Integer size = 20;
}
