package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 提现对账批次状态。
 *
 * <p><b>重入语义（写死）</b>：
 * <ul>
 *   <li>{@code PENDING / FETCHED} 可重入：同 {@code batch_no} 重拉会覆盖逐笔（幂等键见 item 唯一键），
 *       因为"还没闭环"的批次允许补拉/重跑；</li>
 *   <li>{@code MATCHED / DIFF} <b>不可重入</b>：要重跑必须**新建批次**（换 {@code batch_no}）。</li>
 *   <li>{@code RESOLVED → RESOLVED} 允许：<b>处置错误时要能改结论</b>，但每次改都必须写一条
 *       {@code reconciliation_resolution_log}（否则审计断链）。</li>
 * </ul>
 *
 * <p><b>注意</b>：本状态机不进 CI 门禁意义上的"账平"判定；它只描述批次自己走到哪一步。
 */
@Getter
public enum ReconciliationStatus {

    PENDING(1, "待拉取"),
    FETCHED(2, "已拉取"),
    MATCHED(3, "全匹配"),
    DIFF(4, "有差异"),
    RESOLVED(5, "已处置");

    private final int code;
    private final String desc;

    ReconciliationStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 允许的下一跳（唯一出口：任何推进都要过这里）。 */
    public Set<ReconciliationStatus> nextStates() {
        return switch (this) {
            case PENDING -> EnumSet.of(FETCHED);
            case FETCHED -> EnumSet.of(MATCHED, DIFF);
            case MATCHED -> EnumSet.of(RESOLVED);
            case DIFF -> EnumSet.of(RESOLVED);
            case RESOLVED -> EnumSet.of(RESOLVED);   // 只允许"改结论"，且必须留历史
        };
    }

    /** 是否允许从 {@code from} 推到 {@code to}。 */
    public static boolean canTransit(ReconciliationStatus from, ReconciliationStatus to) {
        return from != null && from.nextStates().contains(to);
    }

    /** 该状态是否允许重入（重拉/重跑）。 */
    public boolean reentrant() {
        return this == PENDING || this == FETCHED;
    }
}
