package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.constants.FlashSaleStatusEnum;
import org.icedamericanomall.domain.dto.FlashBuyResult;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.mapper.FlashSaleMapper;
import org.icedamericanomall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀领域服务 — DDD 合规 V5.0
 *
 * 职责: 秒杀活动查询 + Redis Lua 原子库存预扣（单一领域操作）。
 * 跨服务 Feign 调用已上移至 {@link org.icedamericanomall.manager.MarketingManager}。
 */
@Slf4j
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

    /**
     * Redis Lua 原子预扣库存 — 纯领域操作。
     *
     * @return remaining 剩余库存; -1=售罄, -2=超限购, <0=失败
     */
    @Override
    public FlashBuyResult buy(Long flashId, Long addressId) {
        FlashSaleEntity fs = getById(flashId);
        if (fs == null) throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND);
        if (fs.getStatus() != FlashSaleStatusEnum.ACTIVE.getCode())
            throw new BizException(ErrorCode.FLASH_SALE_NOT_STARTED);

        Long userId = UserContext.getUserId();

        long remaining = luaScript.tryDeduct(flashId, userId, 1);
        if (remaining == -1) throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT);
        if (remaining == -2) throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        if (remaining < 0) throw new BizException(ErrorCode.FLASH_SALE_FAILED);

        FlashBuyResult result = new FlashBuyResult();
        result.setSuccess(true);
        result.setFlashId(flashId);
        result.setFlashPrice(fs.getFlashPrice());
        result.setProductId(fs.getProductId());
        result.setSkuId(fs.getSkuId());
        result.setRemainingStock(remaining);
        return result;
    }
}
