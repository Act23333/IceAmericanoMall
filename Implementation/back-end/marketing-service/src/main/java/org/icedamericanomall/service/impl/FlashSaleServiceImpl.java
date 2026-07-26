package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.constants.FlashSaleStatusEnum;
import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.mapper.FlashSaleMapper;
import org.icedamericanomall.producer.FlashSaleOrderPublisher;
import org.icedamericanomall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * V4.0: 秒杀漏斗模型 — Redis Lua 预扣 → RocketMQ 异步 → Consumer 写 MySQL。
 * <p>
 * 大厂对标：京东秒杀漏斗（Redis预减→MQ削峰→DB落库）。
 */
@Slf4j
@Service
public class FlashSaleServiceImpl extends ServiceImpl<FlashSaleMapper, FlashSaleEntity> implements FlashSaleService {

    private final FlashSaleLuaScript luaScript;
    private final FlashSaleOrderPublisher publisher;

    public FlashSaleServiceImpl(FlashSaleLuaScript luaScript, FlashSaleOrderPublisher publisher) {
        this.luaScript = luaScript;
        this.publisher = publisher;
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

        // V4.0: Redis Lua 预扣成功 → RocketMQ 异步 → Consumer 写 MySQL（漏斗第三层+第四层）
        FlashSaleOrderMessage msg = new FlashSaleOrderMessage();
        msg.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        msg.setFlashId(flashId);
        msg.setUserId(userId);
        boolean published = publisher.publish(msg);
        if (!published) {
            log.error("Flash order publish failed — rollback Redis stock: flashId={}, userId={}", flashId, userId);
            // 补偿: Redis 回滚库存 + 清除用户限购标记
            luaScript.preloadStock(flashId, (int) remaining);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        }
        log.info("Flash order published: flashId={}, userId={}, remaining={}", flashId, userId, remaining);
        return true;
    }
}
