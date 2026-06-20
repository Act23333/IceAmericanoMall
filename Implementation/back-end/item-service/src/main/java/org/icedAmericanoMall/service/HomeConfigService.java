package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.HomeConfigEntity;
import java.util.List;

public interface HomeConfigService extends IService<HomeConfigEntity> {
    List<HomeConfigEntity> listEnabled();
}
