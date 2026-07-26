package org.icedamericanomall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.HomeConfigEntity;
import java.util.List;

public interface HomeConfigService extends IService<HomeConfigEntity> {
    List<HomeConfigEntity> listEnabled();
}
