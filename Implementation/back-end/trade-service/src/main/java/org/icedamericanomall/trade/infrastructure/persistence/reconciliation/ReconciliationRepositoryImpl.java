package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.trade.domain.enums.ReconciliationStatus;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository;
import org.icedamericanomall.trade.domain.service.ReconciliationSentinels;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 对账仓储实现。
 *
 * <p><b>三条纪律的落点</b>：
 * <ol>
 *   <li>哨兵编码/解码**只在这里**（`ReconciliationSentinels` 不出这个类）；</li>
 *   <li>重拉用**条件删**（状态判断与删除同一条语句），不先查后删；</li>
 *   <li>异常按原因分开（批次不存在 / 两步间状态变了 / 状态本不可重拉 / seq 非法）。</li>
 * </ol>
 */
@Repository
@RequiredArgsConstructor
public class ReconciliationRepositoryImpl implements ReconciliationRepository {

    private final ReconciliationBatchMapper batchMapper;
    private final ReconciliationItemMapper itemMapper;
    private final ReconciliationRawMapper rawMapper;

    @Override
    public Optional<Batch> findActiveBatch(String provider, LocalDate bizDate) {
        return Optional.ofNullable(batchMapper.selectActive(provider, bizDate)).map(this::toBatch);
    }

    @Override
    public void claimBatch(String batchNo, ReconciliationStatus from, ReconciliationStatus to) {
        if (!ReconciliationStatus.canTransit(from, to)) {
            throw new IllegalReconciliationTransitionException(from, to);
        }
        if (batchMapper.claimStatus(batchNo, from.getCode(), to.getCode()) == 0) {
            // expectedFrom 是主信息；快照可能已被覆盖，异常里已标注"可能为旧值"
            throw new ConcurrentClaimException(batchNo, from, currentStatusOrNull(batchNo));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refetch(String batchNo, List<WithdrawalReconciliationComparer.Item> items,
                        List<RawShard> shards) {
        validateShardSeq(shards);

        ReconciliationStatus current = currentStatus(batchNo);
        if (current == ReconciliationStatus.PENDING) {
            // 检查与推进同一事务；FETCHED 不再 claim（白名单里没有 FETCHED→FETCHED，调了会误抛）
            claimBatch(batchNo, ReconciliationStatus.PENDING, ReconciliationStatus.FETCHED);
        } else if (!current.reentrant()) {
            throw new NotRefetchableException(batchNo, current);
        }

        int deleted = itemMapper.deleteByBatchIfRefetchable(batchNo,
                ReconciliationStatus.PENDING.getCode(), ReconciliationStatus.FETCHED.getCode());
        if (deleted == 0) {
            // 0 行有两种可能：本来就是空的（首次拉取），或状态在两步之间被推进 ⇒ 再判一次
            ReconciliationStatus now = currentStatus(batchNo);
            if (!now.reentrant()) {
                throw new StatusChangedException(batchNo, now);
            }
        }
        rawMapper.deleteByBatchIfRefetchable(batchNo,
                ReconciliationStatus.PENDING.getCode(), ReconciliationStatus.FETCHED.getCode());

        for (WithdrawalReconciliationComparer.Item item : items) {
            itemMapper.insert(toPO(batchNo, item));
        }
        for (RawShard shard : shards) {
            ReconciliationRawPO po = new ReconciliationRawPO();
            po.setBatchNo(batchNo);
            po.setSeq(shard.seq());
            po.setPayload(shard.payload());
            rawMapper.insert(po);
        }
    }

    // ─────────────────────────── 内部 ───────────────────────────

    /**
     * 分片序号必须是从 1 开始、连续且唯一。
     *
     * <p>为什么不交给数据库唯一键：那报出来的是 {@code DuplicateKeyException}，
     * 看不出是**调用方分片逻辑错了**。
     */
    private void validateShardSeq(List<RawShard> shards) {
        List<Integer> actual = new ArrayList<>();
        for (RawShard shard : shards) {
            actual.add(shard.seq());
        }
        List<Integer> sorted = new ArrayList<>(actual);
        sorted.sort(Integer::compareTo);
        boolean ok = sorted.size() == actual.size();
        for (int i = 0; ok && i < sorted.size(); i++) {
            ok = sorted.get(i) == i + 1;
        }
        if (!ok) {
            throw new DuplicateSeqException(shards.size(), actual);
        }
    }

    private ReconciliationStatus currentStatus(String batchNo) {
        return batchMapper.selectStatus(batchNo)
                .map(this::statusOf)
                .orElseThrow(() -> new BatchNotFoundException(batchNo));
    }

    private ReconciliationStatus currentStatusOrNull(String batchNo) {
        return batchMapper.selectStatus(batchNo).map(this::statusOf).orElse(null);
    }

    private ReconciliationStatus statusOf(Integer code) {
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalStateException("未知的对账状态码: " + code);
    }

    private Batch toBatch(ReconciliationBatchPO po) {
        return new Batch(po.getBatchNo(), po.getProvider(), po.getBizDate(), statusOf(po.getStatus()),
                po.getSupersededBy());
    }

    /**
     * 逐笔 PO：**空值在这里被编码成哨兵**（列 NOT NULL，NULL 不参与唯一键去重）。
     */
    private ReconciliationItemPO toPO(String batchNo, WithdrawalReconciliationComparer.Item item) {
        ReconciliationItemPO po = new ReconciliationItemPO();
        po.setBatchNo(batchNo);
        po.setPayoutNo(ReconciliationSentinels.encode(item.payoutNo()));
        po.setChannelRef(ReconciliationSentinels.encode(item.channelRef()));
        po.setOursAmount(item.oursAmount());
        po.setChannelAmount(item.channelAmount());
        po.setBizDate(item.bizDate());
        po.setMatchType(matchTypeCode(item.matchType()));
        po.setDiffType(item.diffType() == null ? null : item.diffType().name());
        return po;
    }

    private int matchTypeCode(WithdrawalReconciliationComparer.MatchType matchType) {
        return switch (matchType) {
            case EXACT -> 1;
            case FUZZY -> 2;
            case DIFF -> 3;
        };
    }
}
