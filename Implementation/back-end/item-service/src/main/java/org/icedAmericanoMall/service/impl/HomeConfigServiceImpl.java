package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.HomeConfigEntity;
import org.icedAmericanoMall.mapper.HomeConfigMapper;
import org.icedAmericanoMall.service.HomeConfigService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HomeConfigServiceImpl extends ServiceImpl<HomeConfigMapper, HomeConfigEntity> implements HomeConfigService {
    @Override
    public List<HomeConfigEntity> listEnabled() {
        return lambdaQuery().eq(HomeConfigEntity::getStatus, 1)
                .orderByAsc(HomeConfigEntity::getSortOrder).list();
    }
}
