package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String productId;
    private Long sellerId;
    private Long categoryId;
    private String name;
    private String mainImage;
    private String description;
    private String brand;
    private Integer soldCount;
    private Integer commentCount;
    private Integer status;
    private LocalDateTime publishTime;
    private List<SkuVO> skus;
}
