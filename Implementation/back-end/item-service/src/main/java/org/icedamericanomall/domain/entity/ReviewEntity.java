package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品评价 — DDD 充血模型 V5.0
 *
 * 京东/淘宝标准: 支持商家回复 + 用户追评 + 点赞
 */
@Getter
@TableName("review")
public class ReviewEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long productId;
    private Long orderId;
    private Long skuId;
    private Integer rating;
    private String content;
    private String images;

    // V5.0: 商家回复
    private String reply;
    private LocalDateTime replyTime;

    // V5.0: 用户追评
    private String appendContent;
    private String appendMediaUrls;
    private LocalDateTime appendTime;

    // V5.0: 点赞
    private Integer likeCount;

    // V5.0: 评价标签
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== 领域行为 ====================

    /** 商家回复评价 */
    public void addReply(String replyContent) {
        if (this.reply != null) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价");
        }
        this.reply = replyContent;
        this.replyTime = LocalDateTime.now();
    }

    /** 用户追评 */
    public void addAppend(String content, String mediaUrls) {
        if (this.appendContent != null) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已追评过该评价");
        }
        this.appendContent = content;
        this.appendMediaUrls = mediaUrls;
        this.appendTime = LocalDateTime.now();
    }

    /** 点赞 (内存计数) */
    public void incrementLike() {
        this.likeCount = (this.likeCount == null ? 0 : this.likeCount) + 1;
    }

    /** 取消点赞 */
    public void decrementLike() {
        if (this.likeCount != null && this.likeCount > 0) {
            this.likeCount--;
        }
    }

    // ==================== 持久层 setter ====================

    public void setId(Long v) { this.id = v; }
    public void setUserId(Long v) { this.userId = v; }
    public void setProductId(Long v) { this.productId = v; }
    public void setOrderId(Long v) { this.orderId = v; }
    public void setSkuId(Long v) { this.skuId = v; }
    public void setRating(Integer v) { this.rating = v; }
    public void setContent(String v) { this.content = v; }
    public void setImages(String v) { this.images = v; }
    public void setReply(String v) { this.reply = v; }
    public void setReplyTime(LocalDateTime v) { this.replyTime = v; }
    public void setAppendContent(String v) { this.appendContent = v; }
    public void setAppendMediaUrls(String v) { this.appendMediaUrls = v; }
    public void setAppendTime(LocalDateTime v) { this.appendTime = v; }
    public void setLikeCount(Integer v) { this.likeCount = v; }
    public void setTags(String v) { this.tags = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
}
