package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品评价请求 —— 替代 Controller 直接接收 Entity。userId 由登录态注入。
 */
@Data
public class ReviewCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long productId;
    private Long orderId;
    private Long skuId;
    private Integer rating;
    private String content;
    private String images;
}
