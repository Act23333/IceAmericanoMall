package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.AfterSaleEntity;

public interface AfterSaleService extends IService<AfterSaleEntity> {

    boolean existsByOrderAndUser(String orderNo, Long userId);

    IPage<AfterSaleEntity> pageByUserId(Long userId, int page, int size);

    IPage<AfterSaleEntity> pageByStatus(Integer status, int page, int size);
}
