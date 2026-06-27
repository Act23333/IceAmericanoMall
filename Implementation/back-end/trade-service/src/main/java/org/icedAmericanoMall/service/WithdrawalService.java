package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;

public interface WithdrawalService extends IService<WithdrawalEntity> {

    IPage<WithdrawalEntity> pageBySeller(Long sellerId, int page, int size);

    IPage<WithdrawalEntity> pageByStatus(Integer status, int page, int size);
}
