package org.icedamericanomall.user.app.favorite;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.app.dto.SpuBriefView;
import org.icedamericanomall.user.app.port.FavoriteSpuBriefQuery;
import org.icedamericanomall.user.domain.model.favorite.Favorite;
import org.icedamericanomall.user.domain.repository.FavoriteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品收藏（应用层）—— 去重收藏（{@code userId + spuId} 幂等）、取消收藏、分页查询。
 *
 * <p>只依赖领域仓储端口 {@link FavoriteRepository}，不直接触碰 Mapper / PO；
 * 列表的商品展示摘要（名称/主图/价格/状态）经读模型端口
 * {@link FavoriteSpuBriefQuery} 按 ID 批量回填（一次查询，无 N+1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FavoriteManager {

    private final FavoriteRepository favoriteRepository;
    private final FavoriteSpuBriefQuery favoriteSpuBriefQuery;

    /**
     * 收藏（幂等：已存在不重复插入）。
     *
     * <p><b>入参收业务键（ADR-061 §决策 2）</b>：{@code spuId} 是 SPU <b>业务键</b> 32-hex；
     * 本层经同库只读读模型解析为技术键后再落 {@code favorite.spu_id BIGINT}（A 案：内部外键
     * 保持技术键不动，解析走 {@code spu.spu_id} 唯一索引，性能与按技术键等价）。
     *
     * @param spuId SPU 业务键（32-hex）
     * @throws BizException 商品不存在（业务键无对应行或格式非法）→ {@code PRODUCT_NOT_FOUND}
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, String spuId) {
        favoriteRepository.addIfAbsent(Favorite.of(userId, resolveSpuTechnicalId(spuId)));
    }

    /** 取消收藏（入参同 {@link #add}：SPU 业务键）。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, String spuId) {
        favoriteRepository.removeByUserIdAndSpuId(userId, resolveSpuTechnicalId(spuId));
    }

    /**
     * 分页查询收藏（按收藏时间倒序），并回填契约 §4.2.6 U20 声明的商品字段与 SPU 业务键。
     *
     * <p>读操作无事务：Feign / 读模型调用一律不入事务。
     */
    public IPage<FavoriteVO> pageByUser(Long userId, int pageNum, int size) {
        long total = favoriteRepository.countByUserId(userId);
        Page<FavoriteVO> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<Favorite> records = favoriteRepository.findPageDescByUserId(userId, pageNum, size);
        Map<Long, SpuBriefView> briefs = loadBriefs(records);
        page.setRecords(records.stream()
                .map(favorite -> FavoriteVO.from(favorite, briefs.get(favorite.getSpuId())))
                .toList());
        page.setPages((long) Math.ceil((double) total / size));
        return page;
    }

    /**
     * SPU 业务键 → 技术键（同库只读，单条查询走 {@code spu.spu_id} 唯一索引）。
     *
     * <p>与列表回填共用同一读模型端口 —— 隔离点在端口之后，将来换内部批量端点时本类零改动。
     *
     * @throws BizException 业务键格式非法或商品行不存在（不伪造、不静默吞掉）
     */
    private Long resolveSpuTechnicalId(String spuBusinessId) {
        Map<String, SpuBriefView> found = favoriteSpuBriefQuery.findByBusinessIds(List.of(spuBusinessId));
        SpuBriefView brief = found == null ? null : found.get(spuBusinessId);
        if (brief == null || brief.spuId() == null) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return brief.spuId();
    }

    /**
     * 批量回填商品展示摘要 —— 整页只打一次数据源（去重后的技术键集合）。
     *
     * <p><b>fail-open（附加展示字段的既有取向）</b>：商品摘要缺失或数据源异常时返回空 Map，
     * 商品字段为 null（{@code spu_id} 亦为 null —— 按 ADR-061 §决策 3 <b>不回落技术键</b>），
     * 收藏列表照常分页返回 —— 绝不因「补展示字段」让主接口变脆。
     */
    private Map<Long, SpuBriefView> loadBriefs(List<Favorite> records) {
        List<Long> spuIds = records.stream()
                .map(Favorite::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (spuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, SpuBriefView> briefs = favoriteSpuBriefQuery.findByIds(spuIds);
            return briefs == null ? Map.of() : briefs;
        } catch (Exception e) {
            log.warn("收藏商品摘要回填失败，商品字段置空（fail-open）: spuIds={}, cause={}",
                    spuIds, e.getMessage());
            return Map.of();
        }
    }
}
