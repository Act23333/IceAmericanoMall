package org.icedAmericanoMall.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.service.impl.FlashSaleServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V3.7: 秒杀异步订单消费者 — RabbitMQ 削峰填谷（漏斗第三层）。
 * <p>
 * 抢到库存的用户 → 发MQ → 消费者异步写 MySQL sold_count。
 * @Version 乐观锁 + sold_count < stock 条件 提供数据库最终防线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleConsumer {

    private final FlashSaleServiceImpl flashSaleService;

    @RabbitListener(queues = "flash.order.queue")
    public void handleFlashOrder(Map<String, Object> msg) {
        Long flashId = Long.valueOf(msg.get("flashId").toString());
        String orderNo = msg.get("orderNo").toString();
        log.info("Flash async persist: flashId={}, orderNo={}", flashId, orderNo);

        FlashSaleEntity fs = flashSaleService.getById(flashId);
        if (fs == null || fs.getSoldCount() >= fs.getStock()) {
            log.warn("Flash {} sold out, skip order {}", flashId, orderNo);
            return;
        }
        // V3.7: 乐观锁 + 条件UPDATE（数据库最终防线）
        boolean ok = flashSaleService.lambdaUpdate()
                .eq(FlashSaleEntity::getId, flashId)
                .eq(FlashSaleEntity::getVersion, fs.getVersion())
                .lt(FlashSaleEntity::getSoldCount, fs.getStock())
                .setSql("sold_count = sold_count + 1")
                .update();
        if (!ok) {
            throw new RuntimeException("Optimistic lock failed for " + orderNo);
        }
        log.info("Flash order {} persisted", orderNo);
    }
}
