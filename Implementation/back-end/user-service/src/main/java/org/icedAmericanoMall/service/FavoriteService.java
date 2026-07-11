package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.FavoriteEntity;
import org.icedAmericanoMall.domain.vo.FavoriteVO;

/**
 * 商品收藏服务 —— 去重收藏、取消收藏、分页查询。
 */
public interface FavoriteService extends IService<FavoriteEntity> {

    void add(Long userId, Long productId);

    void remove(Long userId, Long productId);

    IPage<FavoriteVO> pageByUser(Long userId, int page, int size);
}
