package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.mapper.FlashSaleMapper;
import org.icedAmericanoMall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashSaleServiceImpl extends ServiceImpl<FlashSaleMapper, FlashSaleEntity> implements FlashSaleService {

    @Override
    public List<FlashSaleEntity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery().le(FlashSaleEntity::getStartTime, now)
                .ge(FlashSaleEntity::getEndTime, now)
                .eq(FlashSaleEntity::getStatus, 2).list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean buy(Long flashId) {
        FlashSaleEntity fs = getById(flashId);
        if (fs == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "秒杀活动不存在");
        if (fs.getStatus() != 2) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀未开始或已结束");
        if (fs.getSoldCount() >= fs.getStock()) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已售罄");
        fs.setSoldCount(fs.getSoldCount() + 1);
        return updateById(fs);
    }
}
