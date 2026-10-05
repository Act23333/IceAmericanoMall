package org.icedamericanomall.item.app.content;

import org.icedamericanomall.client.NoteVectorClient;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.item.domain.model.content.Note;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * NoteVectorManager 单元测试 —— V5.13 笔记向量旁路协作器：
 * afterCommit 事务纪律（提交才发送/回滚不发送）、fail-open（哨兵/异常零感知）、删除语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NoteVectorManager 向量旁路协作器测试")
class NoteVectorManagerTest {

    @Mock
    private NoteVectorClient noteVectorClient;

    private NoteVectorManager manager;

    @BeforeEach
    void setUp() {
        manager = new NoteVectorManager(noteVectorClient);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Note publishedNote() {
        Note note = Note.publish(1L, "标题", "正文", null, null);
        note.setId(88L);
        return note;
    }

    @Test
    @DisplayName("ingestAfterCommit — 活动事务内只注册不发送，提交后发送 deleted=false")
    void shouldRegisterAndSendAfterCommit_whenTransactionActive() {
        TransactionSynchronizationManager.initSynchronization();

        manager.ingestAfterCommit(publishedNote());

        verify(noteVectorClient, never()).upsert(any(NoteVectorUpsertCO.class));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<NoteVectorUpsertCO> captor = ArgumentCaptor.forClass(NoteVectorUpsertCO.class);
        verify(noteVectorClient).upsert(captor.capture());
        assertFalse(captor.getValue().isDeleted());
        assertEquals(88L, captor.getValue().getNoteId());
    }

    @Test
    @DisplayName("ingestAfterCommit — 事务回滚（同步器随回滚丢弃）→ 不摄取")
    void shouldNotSend_whenTransactionRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        manager.ingestAfterCommit(publishedNote());

        TransactionSynchronizationManager.clearSynchronization();

        verify(noteVectorClient, never()).upsert(any(NoteVectorUpsertCO.class));
    }

    @Test
    @DisplayName("removeAfterCommit — 软删：发送 deleted=true（提交后生效）")
    void shouldSendDeletedTrue_whenRemoveAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        Note deleted = publishedNote();
        deleted.softDelete();

        manager.removeAfterCommit(deleted);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<NoteVectorUpsertCO> captor = ArgumentCaptor.forClass(NoteVectorUpsertCO.class);
        verify(noteVectorClient).upsert(captor.capture());
        assertTrue(captor.getValue().isDeleted());
    }

    @Test
    @DisplayName("无活动事务 → 立即发送（旁路直发口径）")
    void shouldSendImmediately_whenNoTransactionActive() {
        manager.ingestAfterCommit(publishedNote());

        verify(noteVectorClient).upsert(any(NoteVectorUpsertCO.class));
    }

    @Test
    @DisplayName("降级哨兵 -1 → 只记日志，主流程不抛异常")
    void shouldNotThrow_whenUpsertReturnsSentinel() {
        when(noteVectorClient.upsert(any(NoteVectorUpsertCO.class))).thenReturn(-1);

        assertDoesNotThrow(() -> manager.ingestAfterCommit(publishedNote()));
    }

    @Test
    @DisplayName("发送异常 → 吞掉只记日志，主流程不抛异常")
    void shouldNotThrow_whenUpsertThrows() {
        when(noteVectorClient.upsert(any(NoteVectorUpsertCO.class)))
                .thenThrow(new RuntimeException("search down"));

        assertDoesNotThrow(() -> manager.ingestAfterCommit(publishedNote()));
    }

    @Test
    @DisplayName("未落库笔记（id 为空）→ 静默跳过，零远程调用")
    void shouldSkip_whenNoteNotPersisted() {
        Note unpersisted = Note.publish(1L, "标题", "正文", null, null);

        manager.ingestAfterCommit(unpersisted);

        verifyNoInteractions(noteVectorClient);
    }
}
