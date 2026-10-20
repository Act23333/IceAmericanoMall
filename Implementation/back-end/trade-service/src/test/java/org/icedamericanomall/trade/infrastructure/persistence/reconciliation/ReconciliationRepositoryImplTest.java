package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import org.icedamericanomall.trade.domain.enums.ReconciliationStatus;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository.RawShard;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.DiffType;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Item;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.MatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓储实现的**逻辑**验证（mapper 全桩，不碰库）。
 *
 * <p>覆盖：白名单、并发 0 行、不可重拉、批次不存在、分片 seq、哨兵编码。
 * <b>不覆盖</b>：SQL 正确性、事务回滚、并发真实行为 —— 那些归 H2 用例。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationRepositoryImpl 逻辑（mapper 桩）")
class ReconciliationRepositoryImplTest {

    private static final String BATCH = "B-1";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    @Mock private ReconciliationBatchMapper batchMapper;
    @Mock private ReconciliationItemMapper itemMapper;
    @Mock private ReconciliationRawMapper rawMapper;
    @InjectMocks private ReconciliationRepositoryImpl repository;

    @Test
    @DisplayName("claimBatch：白名单不允许 ⇒ 抛非法迁移，且不碰数据库")
    void shouldRejectIllegalTransition() {
        assertThrows(ReconciliationRepository.IllegalReconciliationTransitionException.class,
                () -> repository.claimBatch(BATCH, ReconciliationStatus.PENDING, ReconciliationStatus.MATCHED));
        verify(batchMapper, never()).claimStatus(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("claimBatch：条件更新 0 行 ⇒ 抛并发异常（expectedFrom 为主，快照来自补查）")
    void shouldThrowConcurrentClaim_whenZeroRows() {
        when(batchMapper.claimStatus(BATCH, 1, 2)).thenReturn(0);
        when(batchMapper.selectStatus(BATCH)).thenReturn(Optional.of(3));

        ReconciliationRepository.ConcurrentClaimException ex = assertThrows(
                ReconciliationRepository.ConcurrentClaimException.class,
                () -> repository.claimBatch(BATCH, ReconciliationStatus.PENDING, ReconciliationStatus.FETCHED));

        assertEquals(ReconciliationStatus.PENDING, ex.getExpectedFrom(), "主信息是我期望的状态");
        assertEquals(ReconciliationStatus.MATCHED, ex.getActualSnapshot());
    }

    @Test
    @DisplayName("refetch：批次不存在 ⇒ BatchNotFoundException")
    void shouldThrowNotFound_whenBatchMissing() {
        when(batchMapper.selectStatus(BATCH)).thenReturn(Optional.empty());

        assertThrows(ReconciliationRepository.BatchNotFoundException.class,
                () -> repository.refetch(BATCH, List.of(), List.of()));
    }

    @Test
    @DisplayName("refetch：已闭环 ⇒ NotRefetchableException，且不删不插")
    void shouldThrowNotRefetchable_whenClosed() {
        when(batchMapper.selectStatus(BATCH)).thenReturn(Optional.of(3));

        assertThrows(ReconciliationRepository.NotRefetchableException.class,
                () -> repository.refetch(BATCH, List.of(), List.of()));
        verify(itemMapper, never()).deleteByBatchIfRefetchable(anyString(), anyInt(), anyInt());
        verify(itemMapper, never()).insert(any(ReconciliationItemPO.class));
    }

    @Test
    @DisplayName("refetch：分片 seq 重复 ⇒ 抛 seq 异常（在任何数据库操作之前）")
    void shouldRejectDuplicateSeq() {
        List<RawShard> shards = List.of(new RawShard(1, new byte[] {1}), new RawShard(1, new byte[] {2}));

        assertThrows(ReconciliationRepository.DuplicateSeqException.class,
                () -> repository.refetch(BATCH, List.of(), shards));
        verify(batchMapper, never()).selectStatus(anyString());
    }

    @Test
    @DisplayName("refetch：PENDING ⇒ 先 claim 到 FETCHED；空值逐笔落库前被编码成哨兵")
    void shouldClaimThenInsertWithSentinel() {
        when(batchMapper.selectStatus(BATCH)).thenReturn(Optional.of(1));
        when(batchMapper.claimStatus(BATCH, 1, 2)).thenReturn(1);
        Item diffItem = new Item("WD-1", null, 4_000, null, DAY, MatchType.DIFF, DiffType.OURS_ONLY);

        repository.refetch(BATCH, List.of(diffItem), List.of(new RawShard(1, new byte[] {7})));

        verify(batchMapper).claimStatus(BATCH, 1, 2);
        ArgumentCaptor<ReconciliationItemPO> captor = ArgumentCaptor.forClass(ReconciliationItemPO.class);
        verify(itemMapper).insert((ReconciliationItemPO) captor.capture());
        assertEquals("__NULL__", captor.getValue().getChannelRef(), "空值必须编码成哨兵，否则 NULL 不参与唯一键去重");
        assertEquals("WD-1", captor.getValue().getPayoutNo(), "有值不动");
    }
}
