package org.icedamericanomall.item.app.content;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 种草笔记编排（应用层）—— F12 内容社区 Phase 1（P28-P33，ADR-048）。
 *
 * <p><b>用例族</b>：发布（P28，关联商品存在且在售的跨聚合校验在本层）、流（P29）、
 * 详情（P30，liked 回填 + 商品摘要卡本地装配）、点赞/取消（P31/P32，uk 幂等 + 原子计数）、
 * 作者软删（P33，归属校验）。
 *
 * <p><b>事务纪律</b>：发布/点赞/取消/软删均为纯本地写，事务边界在本层且显式
 * {@code rollbackFor}；无 Feign 依赖（商品摘要卡同库直查，零跨服务——ADR-048 落位理由）。
 * <b>计数纪律</b>：点赞计数增减为仓储单条原子 SQL，本层不做读-改-写。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteManager {

    private final NoteRepository noteRepository;
    private final NoteLikeRepository noteLikeRepository;
    private final NoteProductRepository noteProductRepository;
    private final ProductRepository productRepository;
    private final SkuManager skuManager;
    private final NoteConverter noteConverter;

    /**
     * 发布种草笔记（P28）—— 聚合工厂校验文本/图集不变式，关联商品逐个校验
     * 存在且在售（任一不过 → 400，消息指明 productId），发布即上架（status 恒 1）。
     *
     * @param userId 作者 user_id（登录态注入）
     * @param req    发布请求
     * @return 含回填 id 的 NoteVO
     */
    @Transactional(rollbackFor = Exception.class)
    public NoteVO publish(Long userId, NotePublishReq req) {
        Note note = Note.publish(userId, req.getTitle(), req.getContent(),
                req.getCoverImage(), req.getImages());
        List<Long> productIds = distinctProductIds(req.getProductIds());
        assertProductsPublishable(productIds);
        noteRepository.save(note);
        if (!productIds.isEmpty()) {
            noteProductRepository.saveAll(note.getId(), productIds);
        }
        log.info("[Note] [publish] [ok] userId={}, noteId={}, productCount={}",
                userId, note.getId(), productIds.size());
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
        vo.setProducts(assembleProducts(noteProductRepository.findProductIdsByNoteId(noteId)));
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
     * 关联 note_product / note_like 保留不清理（软删后不可达）。
     *
     * @throws BizException 笔记不存在或已删除（{@code NO_RESOURCE_FOUND}）、非作者（{@code FORBIDDEN}）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long noteId) {
        Note note = requireVisible(noteId);
        note.assertDeletableBy(userId);
        note.softDelete();
        noteRepository.updateStatus(noteId, note.getStatus());
        log.info("[Note] [delete] [ok] userId={}, noteId={}", userId, noteId);
    }

    // ==================== 私有方法 ====================

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
    private List<Long> distinctProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = productIds.stream().distinct().toList();
        if (distinct.size() > Note.MAX_PRODUCTS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "关联商品数量不能超过" + Note.MAX_PRODUCTS);
        }
        return distinct;
    }

    /** 逐个校验关联商品存在且在售（跨聚合协作，批量查本地零 N+1；不过 → 400 并指明 productId）。 */
    private void assertProductsPublishable(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        Map<Long, Product> byId = productRepository.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        for (Long productId : productIds) {
            Product product = byId.get(productId);
            if (product == null || !onShelf(product)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "商品不存在或已下架: productId=" + productId);
            }
        }
    }

    /**
     * 装配关联商品摘要卡：按 sort 序、过滤非在售（下架/删除一并剔除）、批量回填
     * 在售 SKU 最低价（{@code SkuManager.getMinPriceMapByProductIds}，对齐商品列表卡口径，无 N+1）。
     */
    private List<NoteDetailVO.ProductSummaryVO> assembleProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> onShelfById = productRepository.findAllByIds(productIds).stream()
                .filter(NoteManager::onShelf)
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        List<Long> visibleIds = productIds.stream().filter(onShelfById::containsKey).toList();
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> minPriceMap = skuManager.getMinPriceMapByProductIds(visibleIds);
        return visibleIds.stream()
                .map(id -> toSummary(onShelfById.get(id), minPriceMap.get(id)))
                .toList();
    }

    private static boolean onShelf(Product product) {
        return product.getStatus() != null
                && product.getStatus() == ProductStatusEnum.ON_SHELF.getCode();
    }

    private static NoteDetailVO.ProductSummaryVO toSummary(Product product, Integer minPrice) {
        NoteDetailVO.ProductSummaryVO vo = new NoteDetailVO.ProductSummaryVO();
        vo.setProductId(product.getId());
        vo.setName(product.getName());
        vo.setMainImage(product.getMainImage());
        vo.setMinPrice(minPrice);
        return vo;
    }
}
