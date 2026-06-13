package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.SellerEntity;

public interface SellerService extends IService<SellerEntity> {

    SellerEntity getByUserId(Long userId);

    void register(SellerEntity seller);

    void updateStatus(Long id, Integer status);
}
