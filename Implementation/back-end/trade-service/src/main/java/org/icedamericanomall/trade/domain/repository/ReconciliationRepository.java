package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.enums.ReconciliationStatus;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Item;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 提现对账仓储（端口）。
 *
 * <p><b>三条纪律</b>（写在这里，因为它约束的是实现而不是调用方）：
 * <ol>
 *   <li><b>哨兵只在实现里</b>：本接口的入参与返回值都用<b>原始值</b>（{@code null} 就是 {@code null}），
 *       {@code ReconciliationSentinels} 的编码/解码只允许出现在实现类里 ——
 *       否则哨兵会顺着调用链泄漏进报表与日志。</li>
 *   <li><b>只放行用条件删/锁行，不用"先查后删"</b>：先查再删之间存在窗口，
 *       别人可能在这期间把批次推进到闭环，而我们照样把它的逐笔删了。</li>
 *   <li><b>异常要能区分原因</b>：见各内部异常类，消息必须不同，否则排查时只能猜。</li>
 * </ol>
 */
public interface ReconciliationRepository {

    /** 批次视图（不暴露 PO）。 */
    record Batch(String batchNo, String provider, LocalDate bizDate, ReconciliationStatus status,
                 String supersededBy) {
    }

    /**
     * 原始报文分片。
     *
     * <p><b>分片由调用方（拉取器）决定</b>：每片条数、超长单条单独成片、{@code seq} 从 1 递增 ——
     * 这些属于报文形态知识，不该焊在仓储层。仓储只负责落库与唯一键。
     *
     * @param seq     分片序号（从 1 连续）
     * @param payload 分片内容（可压缩）
     */
    record RawShard(int seq, byte[] payload) {
    }

    /**
     * 取当日有效批次。
     *
     * @param provider 渠道标识
     * @param bizDate  账期
     * @return 有效批次；没有则 {@code Optional.empty()}（这不是异常）
     */
    Optional<Batch> findActiveBatch(String provider, LocalDate bizDate);

    /**
     * 推进批次状态（先白名单校验，再条件更新）。
     *
     * @param batchNo 批次号
     * @param from    期望的当前状态
     * @param to      目标状态
     * @throws IllegalReconciliationTransitionException 白名单不允许 {@code from → to}
     * @throws ConcurrentClaimException                 条件更新 0 行（被别人抢先或状态已变）
     */
    void claimBatch(String batchNo, ReconciliationStatus from, ReconciliationStatus to);

    /**
     * 重拉：同事务先删后插。
     *
     * <p>只放行 {@code PENDING}（此时实现内部会先 claim 到 {@code FETCHED}）与 {@code FETCHED}
     * （直接删后插，不调 claim —— 白名单里没有 {@code FETCHED → FETCHED} 这一跳，调了会误抛）。
     *
     * @param batchNo 批次号
     * @param items   逐笔（原始值，空由实现编码）
     * @param shards  报文分片（调用方已切好）
     * @throws BatchNotFoundException      批次不存在
     * @throws StatusChangedException      查状态与条件删之间状态已变
     * @throws NotRefetchableException     状态本身不可重拉（已闭环或已取代）
     * @throws DuplicateSeqException       分片 seq 不唯一或不连续
     */
    void refetch(String batchNo, List<Item> items, List<RawShard> shards);

    /** 白名单不允许该迁移（编程错误，通常是在调用方写错了 from/to）。 */
    class IllegalReconciliationTransitionException extends RuntimeException {
        public IllegalReconciliationTransitionException(ReconciliationStatus from, ReconciliationStatus to) {
            super("对账状态不允许从 " + from + " 推进到 " + to);
        }
    }

    /**
     * 条件更新 0 行。
     *
     * <p>{@code expectedFrom} 是主信息（"我以为它是 X"）；{@code actualSnapshot} 只是某一刻的快照，
     * 消息里明确标注<b>可能为旧值</b> —— 否则排查时会把快照当事实。
     */
    class ConcurrentClaimException extends RuntimeException {
        private final String batchNo;
        private final ReconciliationStatus expectedFrom;
        private final ReconciliationStatus actualSnapshot;

        public ConcurrentClaimException(String batchNo, ReconciliationStatus expectedFrom,
                                        ReconciliationStatus actualSnapshot) {
            super("批次 " + batchNo + " 并发推进失败：期望状态 " + expectedFrom
                    + "，实际快照 " + actualSnapshot + "（可能为旧值）");
            this.batchNo = batchNo;
            this.expectedFrom = expectedFrom;
            this.actualSnapshot = actualSnapshot;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public ReconciliationStatus getExpectedFrom() {
            return expectedFrom;
        }

        public ReconciliationStatus getActualSnapshot() {
            return actualSnapshot;
        }
    }

    /** 批次不存在（传错 batchNo，或已被清理）。 */
    class BatchNotFoundException extends RuntimeException {
        public BatchNotFoundException(String batchNo) {
            super("对账批次不存在: " + batchNo);
        }
    }

    /** 查状态与条件删之间状态已变（并发推进，不是调用方的错）。 */
    class StatusChangedException extends RuntimeException {
        private final ReconciliationStatus actualSnapshot;

        public StatusChangedException(String batchNo, ReconciliationStatus actualSnapshot) {
            super("批次 " + batchNo + " 在重拉过程中状态已变：实际快照 " + actualSnapshot + "（可能为旧值）");
            this.actualSnapshot = actualSnapshot;
        }

        public ReconciliationStatus getActualSnapshot() {
            return actualSnapshot;
        }
    }

    /** 状态本身不可重拉（已闭环，或已被新批次取代）—— 要重跑必须换 batchNo。 */
    class NotRefetchableException extends RuntimeException {
        public NotRefetchableException(String batchNo, ReconciliationStatus status) {
            super("批次 " + batchNo + " 状态为 " + status + "，不可重拉；要重跑请新建批次");
        }
    }

    /** 分片 seq 重复或不连续（调用方分片逻辑错，别让它撞数据库唯一键）。 */
    class DuplicateSeqException extends RuntimeException {
        public DuplicateSeqException(int expectedCount, List<Integer> actual) {
            super("分片 seq 非法：期望 1.." + expectedCount + "，实际 " + actual);
        }
    }
}
