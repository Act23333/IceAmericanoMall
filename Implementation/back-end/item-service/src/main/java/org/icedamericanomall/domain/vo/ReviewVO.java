package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品评价视图对象 — V5.0 增强
 */
@Data
public class ReviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private Long productId;
    private Long orderId;
    private Long skuId;
    private Integer rating;
    private String content;
    private String images;
    /** V5.0: 评价标签 ["质量好","物流快"] */
    private String tags;

    // V5.0: 商家回复
    private String reply;
    private LocalDateTime replyTime;

    // V5.0: 追评
    private String appendContent;
    private String appendMediaUrls;
    private LocalDateTime appendTime;

    // V5.0: 点赞
    private Integer likeCount;
    private Boolean likedByMe;

    private LocalDateTime createTime;
}
