package org.icedamericanomall.trade.domain.service;

/**
 * 对账差异行的 NULL 侧哨兵。
 *
 * <p><b>为什么需要</b>：`reconciliation_item` 的幂等键是 `UNIQUE(batch_no, payout_no, channel_ref)`，
 * 而 MySQL 的唯一键对 {@code NULL} <b>不去重</b> —— 差异行天然有一侧为空（渠道独有 / 我方独有），
 * 直接塞 NULL 会让同一差异被重复插入。所以落库前把 NULL 换成哨兵，读出时再还原。
 *
 * <p><b>为什么不用 {@code -}</b>：短横在幂等键与渠道流水号里都可能出现，撞车概率不能忽略。
 * {@code __NULL__} 八字符，`payout_no` 64 / `channel_ref` 160 都放得下，形态也一眼可辨。
 *
 * <p><b>反向风险（必须挡）</b>：如果某个真实值**恰好等于**哨兵，读出时会被还原成 NULL，
 * 等于凭空造出一个 NULL —— 所以写入侧**拒绝**该值，而不是默默编码。
 */
public final class ReconciliationSentinels {

    /** NULL 的落库形态。 */
    public static final String NULL_SENTINEL = "__NULL__";

    private ReconciliationSentinels() {
    }

    /**
     * 编码：NULL → 哨兵。
     *
     * @param value 原值（可为 null）
     * @return 落库值
     * @throws IllegalArgumentException 原值恰好等于哨兵（会被误还原成 NULL）
     */
    public static String encode(String value) {
        if (value == null) {
            return NULL_SENTINEL;
        }
        if (isSentinel(value)) {
            throw new IllegalArgumentException("值恰好等于对账哨兵 " + NULL_SENTINEL + "，会被误还原成 NULL");
        }
        return value;
    }

    /**
     * 是否哨兵：**大小写不敏感**比较。
     *
     * <p>为什么不用 {@code equals}：MySQL 的列 collation 可能是 {@code _ci}（大小写不敏感），
     * 一旦库里存的是 {@code __null__}，用 {@code equals} 就认不出来、把占位当真实值放进报表。
     * 这里不依赖数据库 collation，由代码自己判。
     */
    public static boolean isSentinel(String value) {
        return value != null && NULL_SENTINEL.equalsIgnoreCase(value);
    }

    /**
     * 解码：哨兵 → NULL。
     *
     * @param stored 落库值
     * @return 原值
     */
    public static String decode(String stored) {
        return isSentinel(stored) ? null : stored;
    }
}
