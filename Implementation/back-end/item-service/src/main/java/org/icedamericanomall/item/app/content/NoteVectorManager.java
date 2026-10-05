package org.icedamericanomall.item.app.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NoteVectorClient;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.fallback.NoteVectorClientFallback;
import org.icedamericanomall.item.domain.model.content.Note;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 笔记向量推荐协作器（契约 §4.14.10 I50-I51，V5.13；ADR-046 管道第二次复用）——
 * 笔记向量摄取/删除旁路的唯一收口（镜像 {@code ProductRecommendManager} 模式）。
 *
 * <p><b>事务纪律（硬约束：Feign 绝不入事务）</b>：摄取/删除发生在笔记落库成功<b>之后</b>——
 * <ul>
 *   <li>调用方处于活动事务（如 {@code NoteManager#publish} / {@code NoteManager#delete}）→
 *       注册 {@code afterCommit} 回调，事务提交后才发送：回滚即不摄取/不删向量；
 *       CO 在注册时由本次落库的聚合快照构建，字段值在提交前冻结；</li>
 *   <li>无活动事务 → 直接发送。</li>
 * </ul>
 * 发送为 fail-open：降级哨兵 {@code -1} / 任何异常只记日志，笔记主流程零感知
 * （对齐 {@code ProductRecommendManager} 先例；向量可由 reindex 全量重建补齐）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteVectorManager {

    private final NoteVectorClient noteVectorClient;

    /**
     * 笔记向量摄取（发布，契约 I50 幂等覆盖写，事务外旁路）。
     *
     * <p>未落库（id 为空）的笔记静默跳过，不产生任何远程调用。
     *
     * @param note 刚落库的笔记聚合（含技术主键，deleted=false 语义）
     */
    public void ingestAfterCommit(Note note) {
        if (note == null || note.getId() == null) {
            log.debug("笔记向量摄取跳过：笔记未落库");
            return;
        }
        sendAfterCommit(buildUpsertCO(note, false));
    }

    /**
     * 笔记向量删除（软删，契约 I50 deleted=true 删除语义，事务外旁路）。
     *
     * <p>未落库（id 为空）的笔记静默跳过；title/content 由聚合快照冻结
     * （search 侧按 deleted=true 删向量，其余字段忽略，传真值仅为契约字段完整）。
     *
     * @param note 已软删的笔记聚合（status=2，删除语义）
     */
    public void removeAfterCommit(Note note) {
        if (note == null || note.getId() == null) {
            log.debug("笔记向量删除跳过：笔记未落库");
            return;
        }
        sendAfterCommit(buildUpsertCO(note, true));
    }

    // ==================== 私有方法 ====================

    /** 聚合快照 → 摄取 CO（{@code noteId} 为幂等键，重写整份笔记向量文档）。 */
    private NoteVectorUpsertCO buildUpsertCO(Note note, boolean deleted) {
        return new NoteVectorUpsertCO(note.getId(), note.getTitle(), note.getContent(), deleted);
    }

    /**
     * 事务注册：有活动事务（发布/软删均为本层写事务）→ {@code afterCommit} 后发送；
     * 无活动事务 → 直接发送。
     */
    private void sendAfterCommit(NoteVectorUpsertCO co) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendSafely(co);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendSafely(co);
            }
        });
    }

    /** 旁路发送：降级哨兵 {@code -1} / 异常只记日志，主流程零感知（fail-open，ADR-046）。 */
    private void sendSafely(NoteVectorUpsertCO co) {
        try {
            int result = noteVectorClient.upsert(co);
            if (result == NoteVectorClientFallback.UPSERT_FALLBACK_SENTINEL) {
                log.warn("笔记向量摄取降级（哨兵-1），向量未更新可由 reindex 补齐: noteId={}, deleted={}",
                        co.getNoteId(), co.isDeleted());
            } else {
                log.debug("笔记向量摄取完成: noteId={}, deleted={}, result={}",
                        co.getNoteId(), co.isDeleted(), result);
            }
        } catch (Exception e) {
            log.warn("笔记向量摄取失败，笔记主流程不受影响: noteId={}, deleted={}",
                    co.getNoteId(), co.isDeleted(), e);
        }
    }
}
