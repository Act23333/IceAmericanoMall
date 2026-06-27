package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;

public interface ApplicationService extends IService<SellerApplicationEntity> {

    boolean hasPendingApplication(Long userId);

    SellerApplicationEntity getLatestApplication(Long userId);
}
