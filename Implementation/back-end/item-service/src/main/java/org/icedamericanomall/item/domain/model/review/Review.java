package org.icedamericanomall.item.domain.model.review;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 商品评价聚合根（纯 POJO，零框架依赖）—— V5.0 增强：商家回复 + 用户追评 + 点赞。
 *
 * <p><b>聚合边界</b>：{@code review} 行为本聚合根；点赞关联表 {@code review_like}
 * （评价 × 用户）是<b>聚合内部实体</b>，只允许经 {@code ReviewRepository} 的点赞端口访问，
 * 外部不得直接操作该表。
 *
 * <p><b>行为内聚（V5.7 代码级修正）</b>：原实现的 {@code reply IS NULL} /
 * {@code append_content IS NULL AND user_id = ?} 两条业务规则被写在 SQL 的 {@code WHERE}
 * 里，而实体上已存在的 {@link #reply(String)} / {@link #append(String, String)} 方法是
 * 死代码。本次把规则收回聚合：回复/追评/点赞的合法性由聚合方法判定并抛错，
 * 仓储只负责持久化（{@code UPDATE}），规则不再以 SQL 条件形式散落。
 */
@Data
@Builder
public class Review {

    /** 评价星级下限。 */
    public static final int MIN_RATING = 1;

    /** 评价星级上限。 */
    public static final int MAX_RATING = 5;

    private Long id;

    /** 评价作者（用户技术主键）。 */
    private Long userId;

    private Long productId;

    private Long orderId;

    private Long skuId;

    /** 星级 1~5（历史脏数据可能为 null/越界，摘要统计侧已做空值保护）。 */
    private Integer rating;

    private String content;

    /** 评价图片（逗号分隔快照串）。 */
    private String images;

    // ==================== V5.0: 商家回复 ====================

    private String reply;

    private LocalDateTime replyTime;

    // ==================== V5.0: 用户追评 ====================

    private String appendContent;

    private String appendMediaUrls;

    private LocalDateTime appendTime;

    // ==================== V5.0: 点赞 ====================

    private Integer likeCount;

    /** 评价标签（JSON/逗号分隔快照串）。 */
    private String tags;

    private LocalDateTime createTime;

    // ==================== 工厂 ====================

    /**
     * 发表评价（未落库）—— 点赞计数从 0 起算（与历史实现一致）。
     */
    public static Review post(Long userId, Long productId, Long orderId, Long skuId,
                              Integer rating, String content, String images) {
        return Review.builder()
                .userId(userId)
                .productId(productId)
                .orderId(orderId)
                .skuId(skuId)
                .rating(rating)
                .content(content)
                .images(images)
                .likeCount(0)
                .build();
    }

    // ==================== 查询 ====================

    /** 是否已回复。 */
    public boolean isReplied() {
        return this.reply != null;
    }

    /** 是否已追评。 */
    public boolean isAppended() {
        return this.appendContent != null;
    }

    /** 是否为该作者所写。 */
    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    // ==================== 业务断言 / 变更 ====================

    /**
     * 商家回复评价（幂等护栏：已回复不可再次回复）。
     *
     * @param replyContent 回复内容
     * @throws BizException 已回复过该评价（{@code BUSINESS_EXECUTION_EXCEPTION}）
     */
    public void reply(String replyContent) {
        if (isReplied()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价");
        }
        this.reply = replyContent;
        this.replyTime = LocalDateTime.now();
    }

    /**
     * 用户追评（仅评价作者可追评，且不可重复追评）。
     *
     * <p>错误信息与历史实现逐字一致（三种失败原因合并为同一提示，避免探测他人评价）。
     *
     * @throws BizException 评价非本人所有或已追评（{@code BUSINESS_EXECUTION_EXCEPTION}）
     */
    public void append(String content, String mediaUrls) {
        if (isAppended()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "追评失败：评价不存在、非本人评价或已追评");
        }
        this.appendContent = content;
        this.appendMediaUrls = mediaUrls;
        this.appendTime = LocalDateTime.now();
    }

    /**
     * 断言追评人即为评价作者（与 {@link #append(String, String)} 合并为同一错误口径）。
     *
     * @throws BizException 非本人评价（{@code BUSINESS_EXECUTION_EXCEPTION}）
     */
    public void assertAppendableBy(Long userId) {
        if (!isOwnedBy(userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "追评失败：评价不存在、非本人评价或已追评");
        }
    }

    /** 点赞（内存计数；持久化由仓储的原子 SQL 承担）。 */
    public void like() {
        this.likeCount = (this.likeCount == null ? 0 : this.likeCount) + 1;
    }

    /** 取消点赞（内存计数，下限 0）。 */
    public void unlike() {
        if (this.likeCount != null && this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
