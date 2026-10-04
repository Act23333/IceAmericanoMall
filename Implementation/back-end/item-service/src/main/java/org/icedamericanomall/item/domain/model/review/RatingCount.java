package org.icedamericanomall.item.domain.model.review;

/**
 * 评价星级分布投影（领域读模型，框架无关）。
 *
 * <p>来源：{@code SELECT rating, COUNT(*) FROM review WHERE product_id = ? GROUP BY rating}。
 * 由基础设施层映射器产出（外部投影类型见 {@code persistence.review.RatingCountPO}），
 * 再由 {@code ReviewSummaryCalculator} 聚合成 {@link ReviewSummary}。
 *
 * @param rating 星级（历史脏数据可能为 {@code null} 或越界，消费侧需空值保护）
 * @param count  该星级的评价数
 */
public record RatingCount(Integer rating, Long count) {

    /** 空值安全取数（{@code null} 视为 0）。 */
    public long countOrZero() {
        return count == null ? 0L : count;
    }
}
