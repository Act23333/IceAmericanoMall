package org.icedamericanomall.item.app.content;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;
import org.icedamericanomall.item.domain.model.content.Note;
import org.icedamericanomall.item.domain.model.product.Product;
import org.icedamericanomall.item.domain.repository.NoteLikeRepository;
import org.icedamericanomall.item.domain.repository.NoteProductRepository;
import org.icedamericanomall.item.domain.repository.NoteRepository;
import org.icedamericanomall.item.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NoteManager 单元测试 —— F12 种草笔记 P28-P33 编排：发布校验（图集/关联商品）、
 * 流分页、详情装配（liked 两态 + 在售过滤）、点赞 uk 幂等 + 原子计数、作者软删归属；
 * V5.13 补 P34 相似笔记（发布态过滤 + 排除自身 + fail-open）与向量钩子委托。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NoteManager 编排单元测试")
class NoteManagerTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long VIEWER_ID = 2L;
    private static final Long NOTE_ID = 5L;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private NoteLikeRepository noteLikeRepository;

    @Mock
    private NoteProductRepository noteProductRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SkuManager skuManager;

    @Mock
    private NoteConverter noteConverter;

    @Mock
    private NoteVectorManager noteVectorManager;

    @Mock
    private NoteVectorClient noteVectorClient;

    private NoteManager noteManager;

    @BeforeEach
    void setUp() {
        noteManager = new NoteManager(noteRepository, noteLikeRepository,
                noteProductRepository, productRepository, skuManager, noteConverter,
                noteVectorManager, noteVectorClient);
    }

    // ==================== 测试夹具 ====================

    private NotePublishReq req(List<String> images, List<Long> productIds) {
        NotePublishReq req = new NotePublishReq();
        req.setTitle("秋冬通勤穿搭分享");
        req.setContent("这件大衣真的绝了，链接放下面。");
        req.setCoverImage("https://cdn.example.com/cover.jpg");
        req.setImages(images);
        req.setProductIds(productIds);
        return req;
    }

    private Note publishedNote() {
        Note note = Note.publish(AUTHOR_ID, "标题", "正文", null, null);
        note.setId(NOTE_ID);
        note.setLikeCount(3);
        note.setCreateTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        return note;
    }

    private NoteVO voOf(Note note) {
        NoteVO vo = new NoteVO();
        vo.setId(note.getId());
        return vo;
    }

    private NoteCardVO cardOf(Note note) {
        NoteCardVO vo = new NoteCardVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setLikeCount(note.getLikeCount());
        vo.setAuthorId(note.getUserId());
        vo.setCreateTime(note.getCreateTime());
        return vo;
    }

    private NoteDetailVO detailOf(Note note) {
        NoteDetailVO vo = new NoteDetailVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setContent(note.getContent());
        vo.setImages(note.getImages());
        vo.setLikeCount(note.getLikeCount());
        vo.setAuthorId(note.getUserId());
        vo.setCreateTime(note.getCreateTime());
        return vo;
    }

    private Product product(Long id, ProductStatusEnum status) {
        return Product.builder()
                .id(id)
                .name("商品" + id)
                .mainImage("https://cdn.example.com/p" + id + ".jpg")
                .status(status.getCode())
                .build();
    }

    private void stubSaveBackfillsId() {
        doAnswer(inv -> {
            Note note = inv.getArgument(0);
            note.setId(88L);
            return null;
        }).when(noteRepository).save(any(Note.class));
    }

    // ==================== P28 发布 ====================

    @Test
    @DisplayName("publish — 关联商品校验通过：status=1、productIds 去重保序落 note_product、返回回填 id")
    void shouldSaveAndLinkProducts_whenPublishValid() {
        stubSaveBackfillsId();
        when(productRepository.findAllByIds(List.of(10L, 20L)))
                .thenReturn(List.of(product(10L, ProductStatusEnum.ON_SHELF),
                        product(20L, ProductStatusEnum.ON_SHELF)));
        when(noteConverter.toVO(any())).thenAnswer(inv -> voOf(inv.getArgument(0)));
        NotePublishReq req = req(null, List.of(10L, 20L, 10L));

        NoteVO vo = noteManager.publish(AUTHOR_ID, req);

        assertEquals(88L, vo.getId());
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        Note saved = captor.getValue();
        assertEquals(Note.STATUS_PUBLISHED, saved.getStatus());
        assertEquals(0, saved.getLikeCount());
        assertEquals(AUTHOR_ID, saved.getUserId());
        assertTrue(saved.getImages().isEmpty());
        verify(noteProductRepository).saveAll(88L, List.of(10L, 20L));
        // V5.13 向量钩子：发布落库成功后委托协作器（afterCommit 语义归 NoteVectorManager）
        verify(noteVectorManager).ingestAfterCommit(saved);
    }

    @Test
    @DisplayName("publish — 关联商品含非在售/不存在 → PARAM_ERROR 且消息指明 productId，不落库")
    void shouldReject_whenProductOffShelfOrMissing() {
        when(productRepository.findAllByIds(List.of(10L, 20L)))
                .thenReturn(List.of(product(10L, ProductStatusEnum.ON_SHELF)));

        BizException ex = assertThrows(BizException.class,
                () -> noteManager.publish(AUTHOR_ID, req(null, List.of(10L, 20L))));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("productId=20"));
        verify(noteRepository, never()).save(any());

        when(productRepository.findAllByIds(List.of(30L))).thenReturn(List.of());
        BizException missing = assertThrows(BizException.class,
                () -> noteManager.publish(AUTHOR_ID, req(null, List.of(30L))));
        assertEquals(ErrorCode.PARAM_ERROR, missing.getErrorCode());
        assertTrue(missing.getMessage().contains("productId=30"));
        verify(noteRepository, never()).save(any());
    }

    @Test
    @DisplayName("publish — images 超过 9 张 → PARAM_ERROR，聚合工厂拒绝、不触达仓储")
    void shouldReject_whenImagesOverLimit() {
        List<String> images = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "https://cdn.example.com/" + i + ".jpg")
                .toList();

        BizException ex = assertThrows(BizException.class,
                () -> noteManager.publish(AUTHOR_ID, req(images, null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(noteRepository, noteProductRepository);
    }

    @Test
    @DisplayName("publish — 图片 URL 非法（非 http(s)）→ PARAM_ERROR")
    void shouldReject_whenImageUrlInvalid() {
        BizException ex = assertThrows(BizException.class,
                () -> noteManager.publish(AUTHOR_ID, req(List.of("ftp://cdn.example.com/a.jpg"), null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(noteRepository);
    }

    // ==================== P29 流 ====================

    @Test
    @DisplayName("pagePublished — 分页透传仓储（倒序由仓储保证）并转换为轻卡片，顺序保持")
    void shouldReturnCardPage_whenFeedQueried() {
        Note first = publishedNote();
        Note second = publishedNote();
        second.setId(6L);
        Page<Note> page = new Page<>(1, 20);
        page.setRecords(List.of(first, second));
        page.setTotal(2);
        when(noteRepository.pagePublished(1, 20)).thenReturn(page);
        when(noteConverter.toCardVO(any())).thenAnswer(inv -> cardOf(inv.getArgument(0)));

        IPage<NoteCardVO> result = noteManager.pagePublished(1, 20);

        assertEquals(2, result.getRecords().size());
        assertEquals(5L, result.getRecords().get(0).getId());
        assertEquals(6L, result.getRecords().get(1).getId());
        assertEquals(1L, result.getRecords().get(0).getAuthorId());
        assertEquals(3, result.getRecords().get(0).getLikeCount());
    }

    // ==================== P30 详情 ====================

    @Test
    @DisplayName("noteDetail — 已点赞登录用户视角：liked=true")
    void shouldFillLikedTrue_whenViewerLikedDetail() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());
        when(noteLikeRepository.existsByNoteAndUser(NOTE_ID, VIEWER_ID)).thenReturn(true);
        when(noteProductRepository.findProductIdsByNoteId(NOTE_ID)).thenReturn(List.of());
        when(noteConverter.toDetailVO(any())).thenAnswer(inv -> detailOf(inv.getArgument(0)));

        NoteDetailVO vo = noteManager.noteDetail(NOTE_ID, VIEWER_ID);

        assertTrue(vo.getLiked());
        assertEquals(AUTHOR_ID, vo.getAuthorId());
        assertEquals(3, vo.getLikeCount());
        assertTrue(vo.getProducts().isEmpty());
    }

    @Test
    @DisplayName("noteDetail — 匿名视角：liked=false 且不查询点赞表")
    void shouldFillLikedFalse_whenAnonymousDetail() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());
        when(noteProductRepository.findProductIdsByNoteId(NOTE_ID)).thenReturn(List.of());
        when(noteConverter.toDetailVO(any())).thenAnswer(inv -> detailOf(inv.getArgument(0)));

        NoteDetailVO vo = noteManager.noteDetail(NOTE_ID, null);

        assertFalse(vo.getLiked());
        verifyNoInteractions(noteLikeRepository);
    }

    @Test
    @DisplayName("noteDetail — 非在售/缺失商品被过滤，保 sort 序并批量回填最低价")
    void shouldFilterOffShelfProducts_whenDetailAssembled() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());
        when(noteLikeRepository.existsByNoteAndUser(NOTE_ID, VIEWER_ID)).thenReturn(false);
        when(noteProductRepository.findProductIdsByNoteId(NOTE_ID)).thenReturn(List.of(10L, 20L, 30L));
        when(productRepository.findAllByIds(List.of(10L, 20L, 30L)))
                .thenReturn(List.of(product(10L, ProductStatusEnum.ON_SHELF),
                        product(20L, ProductStatusEnum.OFF_SHELF)));
        when(skuManager.getMinPriceMapByProductIds(List.of(10L))).thenReturn(Map.of(10L, 1250));
        when(noteConverter.toDetailVO(any())).thenAnswer(inv -> detailOf(inv.getArgument(0)));

        NoteDetailVO vo = noteManager.noteDetail(NOTE_ID, VIEWER_ID);

        assertEquals(1, vo.getProducts().size());
        NoteDetailVO.ProductSummaryVO summary = vo.getProducts().get(0);
        assertEquals(10L, summary.getProductId());
        assertEquals("商品10", summary.getName());
        assertEquals(1250, summary.getMinPrice());
        verify(skuManager).getMinPriceMapByProductIds(List.of(10L));
    }

    // ==================== P31/P32 点赞 ====================

    @Test
    @DisplayName("like — 首次点赞：插入 like 行成功后计数原子 +1")
    void shouldIncrementCount_whenFirstLike() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());
        when(noteLikeRepository.insertLike(NOTE_ID, VIEWER_ID)).thenReturn(1);

        noteManager.like(VIEWER_ID, NOTE_ID);

        verify(noteRepository).incrementLikeCount(NOTE_ID);
    }

    @Test
    @DisplayName("like — 重复点赞 uk 撞：幂等回落不抛错、不重复计数")
    void shouldNotIncrement_whenDuplicateLike() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());
        when(noteLikeRepository.insertLike(NOTE_ID, VIEWER_ID))
                .thenThrow(new DuplicateKeyException("uk_note_user"));

        assertDoesNotThrow(() -> noteManager.like(VIEWER_ID, NOTE_ID));

        verify(noteRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("like — 笔记不存在 → 1103，不触达点赞表")
    void shouldThrowNotFound_whenLikeMissingNote() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> noteManager.like(VIEWER_ID, NOTE_ID));

        assertEquals(ErrorCode.NO_RESOURCE_FOUND, ex.getErrorCode());
        verifyNoInteractions(noteLikeRepository);
    }

    @Test
    @DisplayName("unlike — 已点赞：删除 like 行成功后计数原子 -1")
    void shouldDecrementCount_whenUnlikeLiked() {
        when(noteLikeRepository.deleteByNoteAndUser(NOTE_ID, VIEWER_ID)).thenReturn(1);

        noteManager.unlike(VIEWER_ID, NOTE_ID);

        verify(noteRepository).decrementLikeCount(NOTE_ID);
    }

    @Test
    @DisplayName("unlike — 未点赞空操作：不触发计数变更")
    void shouldSkipDecrement_whenUnlikeNotLiked() {
        when(noteLikeRepository.deleteByNoteAndUser(NOTE_ID, VIEWER_ID)).thenReturn(0);

        noteManager.unlike(VIEWER_ID, NOTE_ID);

        verify(noteRepository, never()).decrementLikeCount(anyLong());
    }

    // ==================== P33 删除 ====================

    @Test
    @DisplayName("delete — 非作者本人 → FORBIDDEN，不落任何状态变更")
    void shouldThrowForbidden_whenNonAuthorDeletes() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());

        BizException ex = assertThrows(BizException.class,
                () -> noteManager.delete(VIEWER_ID, NOTE_ID));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(noteRepository, never()).updateStatus(anyLong(), anyInt());
    }

    @Test
    @DisplayName("delete — 作者本人软删：status 落 2（流与详情不可见）+ 删向量钩子委托")
    void shouldSoftDelete_whenAuthorDeletes() {
        when(noteRepository.findById(NOTE_ID)).thenReturn(publishedNote());

        noteManager.delete(AUTHOR_ID, NOTE_ID);

        verify(noteRepository).updateStatus(NOTE_ID, Note.STATUS_DELETED);
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteVectorManager).removeAfterCommit(captor.capture());
        assertEquals(Note.STATUS_DELETED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("delete/detail — 已软删笔记对外不可见：删除走 1103、详情走 1103")
    void shouldThrowNotFound_whenNoteDeleted() {
        Note deleted = publishedNote();
        deleted.softDelete();
        when(noteRepository.findById(NOTE_ID)).thenReturn(deleted);

        BizException detailEx = assertThrows(BizException.class,
                () -> noteManager.noteDetail(NOTE_ID, VIEWER_ID));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, detailEx.getErrorCode());

        BizException deleteEx = assertThrows(BizException.class,
                () -> noteManager.delete(AUTHOR_ID, NOTE_ID));
        assertEquals(ErrorCode.NO_RESOURCE_FOUND, deleteEx.getErrorCode());
    }

    // ==================== P34 相似笔记（V5.13） ====================

    @Test
    @DisplayName("similarNotes — 命中含自身与已删笔记：排除自身、过滤未发布、按命中序组装发布态卡")
    void shouldAssembleSimilarCards_whenHitsContainSelfAndDeleted() {
        Note similar = publishedNote();
        similar.setId(6L);
        Note deleted = publishedNote();
        deleted.setId(7L);
        deleted.softDelete();
        when(noteVectorClient.similar(NOTE_ID, 5)).thenReturn(List.of(
                new NoteVectorHitCO(NOTE_ID, 0.98),
                new NoteVectorHitCO(6L, 0.95),
                new NoteVectorHitCO(7L, 0.90)));
        when(noteRepository.findAllByIds(List.of(6L, 7L)))
                .thenReturn(List.of(similar, deleted));
        when(noteConverter.toCardVO(any())).thenAnswer(inv -> cardOf(inv.getArgument(0)));

        List<NoteCardVO> result = noteManager.similarNotes(NOTE_ID, 5);

        assertEquals(1, result.size());
        assertEquals(6L, result.get(0).getId());
    }

    @Test
    @DisplayName("similarNotes — 客户端异常 → 空列表不抛错（fail-open 推荐位口径）")
    void shouldReturnEmpty_whenSimilarClientThrows() {
        when(noteVectorClient.similar(NOTE_ID, 5)).thenThrow(new RuntimeException("search down"));

        assertTrue(noteManager.similarNotes(NOTE_ID, 5).isEmpty());
        verifyNoInteractions(noteRepository);
    }

    @Test
    @DisplayName("similarNotes — 无命中 → 空列表且不触达仓储")
    void shouldReturnEmpty_whenNoSimilarHits() {
        when(noteVectorClient.similar(NOTE_ID, 5)).thenReturn(List.of());

        assertTrue(noteManager.similarNotes(NOTE_ID, 5).isEmpty());
        verify(noteRepository, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("similarNotes — size 归一：≤0 取 5、>20 截 20（透传仓储前的命中数一致）")
    void shouldNormalizeSize_whenSimilarQueried() {
        when(noteVectorClient.similar(anyLong(), anyInt())).thenReturn(List.of());

        noteManager.similarNotes(NOTE_ID, 0);
        noteManager.similarNotes(NOTE_ID, 99);

        verify(noteVectorClient).similar(NOTE_ID, 5);
        verify(noteVectorClient).similar(NOTE_ID, 20);
    }
}
