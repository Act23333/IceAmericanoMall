package org.icedamericanomall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import java.util.List;

public interface FlashSaleService extends IService<FlashSaleEntity> {
    List<FlashSaleEntity> listActive();
    boolean buy(Long flashId);
}
