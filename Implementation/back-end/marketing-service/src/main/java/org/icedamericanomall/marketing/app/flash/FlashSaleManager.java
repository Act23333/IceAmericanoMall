package org.icedamericanomall.marketing.app.flash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.port.FlashStockStore;
import org.icedamericanomall.marketing.domain.repository.FlashSaleRepository;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀用例族编排（应用层）—— 场次查询 + Redis Lua 原子库存预扣 + 异步统计。
 *
 * <p>跨服务 Feign（落单）与 MQ（统计消息）在 {@code app.MarketingManager} 与本类的消费者协同中完成；
 * 本类只含单一聚合内的领域操作，Feign/MQ 不出现在此层之外。
 *
 * <p>库存键缺失时由 Redis 适配器按 DB 剩余库存惰性兜底重建，避免「未预热即售罄」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleManager {

    /** 单人单场限购件数（与 {@code FLASH_SALE_LIMIT_EXCEEDED} 文案「每人限购1件」一致）。 */
    private static final int PURCHASE_LIMIT = 1;

    private final FlashSaleRepository flashSaleRepository;
    private final FlashStockStore flashStockStore;

    /** 进行中的场次列表（时间窗 + 状态，均为 DB 口径）。 */
    public List<FlashSale> listActive() {
        return flashSaleRepository.findActive(LocalDateTime.now());
    }

    /**
     * Redis Lua 原子预扣库存 — 纯领域操作。
     *
     * @param flashId   秒杀活动 ID
     * @param addressId 收货地址 ID（编排层落单使用，预扣不感知）
     * @return 预扣结果；剩余库存见 {@link FlashBuyResult#getRemainingStock()}
     * @throws BizException 场次不存在 / 未开始 / 售罄 / 超限购 / 预扣失败
     */
    public FlashBuyResult buy(Long flashId, Long addressId) {
        FlashSale flashSale = flashSaleRepository.findById(flashId);
        if (flashSale == null) {
            throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND);
        }
        if (!flashSale.isActive()) {
            throw new BizException(ErrorCode.FLASH_SALE_NOT_STARTED);
        }

        Long userId = UserContext.getUserId();
        Duration ttl = MarketingRedisKeys.ttlUntil(flashSale.getEndTime());
        long remaining = flashStockStore.tryDeduct(flashId, userId, PURCHASE_LIMIT,
                flashSale.remainingStock(), ttl);
        if (remaining == FlashStockStore.SOLD_OUT) {
            throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT);
        }
        if (remaining == FlashStockStore.LIMIT_EXCEEDED) {
            throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        }
        if (remaining < 0) {
            throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        }

        FlashBuyResult result = new FlashBuyResult();
        result.setSuccess(true);
        result.setFlashId(flashId);
        result.setFlashPrice(flashSale.getFlashPrice());
        // 此处只回填技术键（本域内部口径）；业务键由 MarketingManager 经读模型解析（ADR-061 §决策 2）
        result.setSkuId(flashSale.getSkuId());
        result.setRemainingStock(remaining);
        return result;
    }

    /**
     * 异步统计 soldCount +1（RocketMQ 消费者调用）。
     *
     * <p>DB 口径售罄则跳过；乐观锁冲突（版本号变化）抛 {@link BizException}
     * 交给 RocketMQ 重试消费——消费者保持重试语义。
     *
     * @param flashId 秒杀活动 ID
     * @param orderNo 订单号（日志标识）
     */
    public void incrementSoldCountAsync(Long flashId, String orderNo) {
        FlashSale flashSale = flashSaleRepository.findById(flashId);
        if (flashSale == null || !flashSale.hasSellableStock()) {
            log.warn("Flash {} sold out, skip stats {}", flashId, orderNo);
            return;
        }
        boolean ok = flashSaleRepository.incrementSoldCountIfBelowStock(
                flashId, flashSale.getVersion(), flashSale.getStock());
        if (!ok) {
            log.warn("Optimistic lock failed for stats {}", orderNo);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED, "秒杀统计乐观锁冲突，等待重试");
        }
        log.info("Flash async stats done: {}", orderNo);
    }
}
