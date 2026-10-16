package org.icedamericanomall.item.app.content;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NoteVectorClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.item.adapter.assembler.NoteConverter;
import org.icedamericanomall.item.adapter.request.NotePublishReq;
import org.icedamericanomall.item.adapter.response.NoteCardVO;
import org.icedamericanomall.item.adapter.response.NoteDetailVO;
import org.icedamericanomall.item.adapter.response.NoteVO;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.content.Note;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.repository.NoteLikeRepository;
import org.icedamericanomall.item.domain.repository.NoteSpuRepository;
import org.icedamericanomall.item.domain.repository.NoteRepository;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 种草笔记编排（应用层）—— F12 内容社区 Phase 1（P28-P33，ADR-048）+ P34 相似笔记（V5.13）。
 *
 * <p><b>用例族</b>：发布（P28，关联商品存在且在售的跨聚合校验在本层）、流（P29）、
 * 详情（P30，liked 回填 + 商品摘要卡本地装配）、点赞/取消（P31/P32，uk 幂等 + 原子计数）、
 * 作者软删（P33，归属校验）、相似笔记（P34，I51 kNN 命中由本地组装发布态卡）。
 *
 * <p><b>事务纪律</b>：发布/点赞/取消/软删均为纯本地写，事务边界在本层且显式
 * {@code rollbackFor}；笔记业务规则零跨服务（商品摘要卡同库直查——ADR-048 落位理由）。
 * 唯一 Feign 依赖是 V5.13 笔记向量化旁路（ADR-046 管道第二次复用）：摄取/删向量经
 * {@link NoteVectorManager} 事务外 afterCommit 收口、相似检索 fail-open——笔记主流程零感知。
 * <b>计数纪律</b>：点赞计数增减为仓储单条原子 SQL，本层不做读-改-写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteManager {

    /** 相似笔记缺省条数（P34 契约：缺省 5）。 */
    private static final int DEFAULT_SIMILAR_SIZE = 5;
    /** 相似笔记单次上限（P34 契约：≤20 归一）。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    private final NoteRepository noteRepository;
    private final NoteLikeRepository noteLikeRepository;
    private final NoteSpuRepository noteSpuRepository;
    private final SpuRepository spuRepository;
    private final SkuManager skuManager;
    private final NoteConverter noteConverter;
    private final NoteVectorManager noteVectorManager;
    private final NoteVectorClient noteVectorClient;

    /**
     * 发布种草笔记（P28）—— 聚合工厂校验文本/图集不变式，关联商品逐个校验
     * 存在且在售（任一不过 → 400，消息指明 spuId），发布即上架（status 恒 1）。
     *
     * @param userId 作者 user_id（登录态注入）
     * @param req    发布请求
     * @return 含回填 id 的 NoteVO
     */
    @Transactional(rollbackFor = Exception.class)
    public NoteVO publish(Long userId, NotePublishReq req) {
        Note note = Note.publish(userId, req.getTitle(), req.getContent(),
                req.getCoverImage(), req.getImages());
        List<Long> spuIds = distinctSpuIds(req.getSpuIds());
        assertSpusPublishable(spuIds);
        noteRepository.save(note);
        if (!spuIds.isEmpty()) {
            noteSpuRepository.saveAll(note.getId(), spuIds);
        }
        // 向量摄取钩子（V5.13，ADR-046 第二次复用）：CO 取自本次落库聚合，事务提交后才旁路发送——回滚即不摄取
        noteVectorManager.ingestAfterCommit(note);
        log.info("[Note] [publish] [ok] userId={}, noteId={}, spuCount={}",
                userId, note.getId(), spuIds.size());
        return noteConverter.toVO(note);
    }

    /**
     * 已发布笔记流（P29）—— status=1 按发布时间倒序分页，轻卡片不含正文；
     * 倒序语义由仓储 SQL 保证（idx_status_time）。
     */
    public IPage<NoteCardVO> pagePublished(int page, int size) {
        return noteRepository.pagePublished(page, size).convert(noteConverter::toCardVO);
    }

    /**
     * 笔记详情（P30）—— 已发布可见；{@code liked} 按登录用户视角回填（匿名恒 false）；
     * 关联商品按 sort 序装配摘要卡并<b>过滤非在售</b>（minPrice 对齐商品列表卡口径）。
     *
     * @throws BizException 笔记不存在或已删除（{@code NO_RESOURCE_FOUND}）
     */
    public NoteDetailVO noteDetail(Long noteId, Long viewerId) {
        Note note = requirePublished(noteId);
        NoteDetailVO vo = noteConverter.toDetailVO(note);
        vo.setLiked(viewerId != null && noteLikeRepository.existsByNoteAndUser(noteId, viewerId));
        vo.setSpus(assembleSpus(noteSpuRepository.findSpuIdsByNoteId(noteId)));
        return vo;
    }

    /**
     * 点赞（P31）—— 笔记须存在且已发布（否则 1103）；先插 {@code note_like}，
     * uk 撞 = 重复点赞，幂等回落为空操作（不报错不重复计数）；插入成功后才原子 +1。
     */
    @Transactional(rollbackFor = Exception.class)
    public void like(Long userId, Long noteId) {
        requirePublished(noteId);
        try {
            noteLikeRepository.insertLike(noteId, userId);
        } catch (DuplicateKeyException e) {
            log.info("[Note] [like] [idempotent] userId={}, noteId={}", userId, noteId);
            return;
        }
        noteRepository.incrementLikeCount(noteId);
    }

    /**
     * 取消点赞（P32）—— 未点赞为空操作；删除 like 行成功后计数原子减 1 且不为负。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long userId, Long noteId) {
        if (noteLikeRepository.deleteByNoteAndUser(noteId, userId) > 0) {
            noteRepository.decrementLikeCount(noteId);
        }
    }

    /**
     * 删除笔记（P33）—— 仅作者本人（非本人 → 403）；软删 status=2 后流与详情不可见，
     * 关联 note_spu / note_like 保留不清理（软删后不可达）。
     *
     * <p>软删落库成功后旁路发删向量指令（V5.13，deleted=true，事务提交后生效）。
     *
     * @throws BizException 笔记不存在或已删除（{@code NO_RESOURCE_FOUND}）、非作者（{@code FORBIDDEN}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long noteId) {
        Note note = requireVisible(noteId);
        note.assertDeletableBy(userId);
        note.softDelete();
        noteRepository.updateStatus(noteId, note.getStatus());
        noteVectorManager.removeAfterCommit(note);
        log.info("[Note] [delete] [ok] userId={}, noteId={}", userId, noteId);
    }

    /**
     * 相似笔记（P34）—— I51 kNN 命中 id 由本地组装**发布态**笔记卡：过滤已删/未发布、
     * 排除自身、按相似度降序（命中序），不足 size 按实际数量返回。
     *
     * <p>推荐位口径（对齐 P27 相似商品）：检索降级/无向量/笔记本身不存在 → 空列表，
     * 不抛 404——相似推荐是纯旁路，缺失不阻断笔记页。
     *
     * @param noteId 笔记 id
     * @param size   期望条数（≤0 归 5，>20 截 20）
     */
    public List<NoteCardVO> similarNotes(Long noteId, int size) {
        int normalized = normalizeSize(size);
        List<Long> hitIds = similarHitIds(noteId, normalized).stream()
                .filter(id -> !id.equals(noteId))
                .toList();
        if (hitIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Note> publishedById = noteRepository.findAllByIds(hitIds).stream()
                .filter(Note::isPublished)
                .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
        return hitIds.stream()
                .map(publishedById::get)
                .filter(Objects::nonNull)
                .map(noteConverter::toCardVO)
                .toList();
    }

    // ==================== 私有方法 ====================

    /** I51 相似命中 id（降序）；调用方侧薄防御层——降级/异常一律空列表（fail-open）。 */
    private List<Long> similarHitIds(Long noteId, int size) {
        try {
            return noteVectorClient.similar(noteId, size).stream()
                    .map(NoteVectorHitCO::getNoteId)
                    .toList();
        } catch (Exception e) {
            log.warn("相似笔记检索失败，推荐位返回空: noteId={}", noteId, e);
            return List.of();
        }
    }

    /** 期望条数归一：≤0 取缺省 5，>20 截 20（P34 契约口径）。 */
    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIMILAR_SIZE;
        }
        return Math.min(size, MAX_SIMILAR_SIZE);
    }

    /** 载入对外可见笔记（不存在或已软删一律 1103——软删后对读者等同不存在）。 */
    private Note requireVisible(Long noteId) {
        Note note = noteRepository.findById(noteId);
        if (note == null || note.isDeleted()) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "笔记不存在或已删除");
        }
        return note;
    }

    /** 载入已发布笔记（P30/P31/P32 前置；Phase 1 仅 1/2 两态，预留给后续审核态）。 */
    private Note requirePublished(Long noteId) {
        Note note = requireVisible(noteId);
        if (!note.isPublished()) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "笔记不存在或已删除");
        }
        return note;
    }

    /** 关联商品去重保序 + 条数守卫（≤5，魔法值口径统一取聚合常量）。 */
    private List<Long> distinctSpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = spuIds.stream().distinct().toList();
        if (distinct.size() > Note.MAX_SPUS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "关联商品数量不能超过" + Note.MAX_SPUS);
        }
        return distinct;
    }

    /** 逐个校验关联商品存在且在售（跨聚合协作，批量查本地零 N+1；不过 → 400 并指明 spuId）。 */
    private void assertSpusPublishable(List<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return;
        }
        Map<Long, Spu> byId = spuRepository.findAllByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, p -> p, (a, b) -> a));
        for (Long spuId : spuIds) {
            Spu spu = byId.get(spuId);
            if (spu == null || !onShelf(spu)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "商品不存在或已下架: spuId=" + spuId);
            }
        }
    }

    /**
     * 装配关联商品摘要卡：按 sort 序、过滤非在售（下架/删除一并剔除）、批量回填
     * 在售 SKU 最低价（{@code SkuManager.getMinPriceMapBySpuIds}，对齐商品列表卡口径，无 N+1）。
     */
    private List<NoteDetailVO.SpuSummaryVO> assembleSpus(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Spu> onShelfById = spuRepository.findAllByIds(spuIds).stream()
                .filter(NoteManager::onShelf)
                .collect(Collectors.toMap(Spu::getId, p -> p, (a, b) -> a));
        List<Long> visibleIds = spuIds.stream().filter(onShelfById::containsKey).toList();
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapBySpuIds(visibleIds);
        return visibleIds.stream()
                .map(id -> toSummary(onShelfById.get(id), minPriceMap.get(id)))
                .toList();
    }

    private static boolean onShelf(Spu spu) {
        return spu.getStatus() != null
                && spu.getStatus() == SpuStatusEnum.ON_SHELF.getCode();
    }

    private static NoteDetailVO.SpuSummaryVO toSummary(Spu spu, Integer minPrice) {
        NoteDetailVO.SpuSummaryVO vo = new NoteDetailVO.SpuSummaryVO();
        vo.setSpuId(spu.getId());
        vo.setName(spu.getName());
        vo.setMainImage(spu.getMainImage());
        vo.setMinPrice(minPrice);
        return vo;
    }
}
