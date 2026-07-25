package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.constants.FlashSaleStatusEnum;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.mapper.FlashSaleMapper;
import org.icedAmericanoMall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V3.7: 秒杀服务 — Redis Lua 原子预扣 + @Version 乐观锁 + FlashSaleStatusEnum 状态机。
 */
@Service
public class FlashSaleServiceImpl extends ServiceImpl<FlashSaleMapper, FlashSaleEntity> implements FlashSaleService {

    private final FlashSaleLuaScript luaScript;

    public FlashSaleServiceImpl(FlashSaleLuaScript luaScript) {
        this.luaScript = luaScript;
    }

    @Override
    public List<FlashSaleEntity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery().le(FlashSaleEntity::getStartTime, now)
                .ge(FlashSaleEntity::getEndTime, now)
                .eq(FlashSaleEntity::getStatus, FlashSaleStatusEnum.ACTIVE.getCode()).list();
    }

    @Override
    public boolean buy(Long flashId) {
        FlashSaleEntity fs = getById(flashId);
        if (fs == null) throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND);
        if (fs.getStatus() != FlashSaleStatusEnum.ACTIVE.getCode())
            throw new BizException(ErrorCode.FLASH_SALE_NOT_STARTED);

        Long userId = UserContext.getUserId();
        long remaining = luaScript.tryDeduct(flashId, userId, 1);

        if (remaining == -1) throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT);
        if (remaining == -2) throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        if (remaining < 0) throw new BizException(ErrorCode.FLASH_SALE_FAILED);

        lambdaUpdate().eq(FlashSaleEntity::getId, flashId)
                .setIncrBy(FlashSaleEntity::getSoldCount, 1).update();
        return true;
    }
}
