package org.icedamericanomall.user.app.favorite;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.domain.model.favorite.Favorite;
import org.icedamericanomall.user.domain.repository.FavoriteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品收藏（应用层）—— 去重收藏（{@code userId + productId} 幂等）、取消收藏、分页查询。
 *
 * <p>只依赖领域仓储端口 {@link FavoriteRepository}，不直接触碰 Mapper / PO。
 */
@Component
@RequiredArgsConstructor
public class FavoriteManager {

    private final FavoriteRepository favoriteRepository;

    /** 收藏（幂等：已存在不重复插入）。 */
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long productId) {
        favoriteRepository.addIfAbsent(Favorite.of(userId, productId));
    }

    /** 取消收藏。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long productId) {
        favoriteRepository.removeByUserIdAndProductId(userId, productId);
    }

    /** 分页查询收藏（按收藏时间倒序）。 */
    public IPage<FavoriteVO> pageByUser(Long userId, int pageNum, int size) {
        long total = favoriteRepository.countByUserId(userId);
        Page<FavoriteVO> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<Favorite> records = favoriteRepository.findPageDescByUserId(userId, pageNum, size);
        page.setRecords(records.stream().map(FavoriteVO::from).toList());
        page.setPages((long) Math.ceil((double) total / size));
        return page;
    }
}
