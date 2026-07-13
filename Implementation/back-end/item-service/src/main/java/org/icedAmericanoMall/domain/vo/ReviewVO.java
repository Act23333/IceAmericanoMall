package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品评价视图对象。
 */
@Data
public class ReviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Long skuId;
    private Integer rating;
    private String content;
    private String images;
    private LocalDateTime createTime;
}
