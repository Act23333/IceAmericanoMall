package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.SkuEntity;

import java.util.List;

public interface SkuService extends IService<SkuEntity> {

    List<SkuEntity> getSkuListByIds(List<Long> skuIds);

    void deductStock(Long skuId, int quantity);

    void restoreStock(Long skuId, int quantity);
}
