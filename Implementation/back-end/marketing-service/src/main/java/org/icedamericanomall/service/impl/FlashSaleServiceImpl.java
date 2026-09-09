package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.constants.FlashSaleStatusEnum;
import org.icedamericanomall.domain.dto.FlashBuyResult;
import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryDTO;
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
 * V4.1: 秒杀漏斗模型 — Redis Lua 预扣 → 调用 trade-service Feign 创建真实订单 → RocketMQ 异步统计。
 * <p>
 * 大厂对标：京东秒杀漏斗（Redis预减 → trade创建订单 → MQ统计落库）。
 * V4.1 改进: 订单不再留在 marketing 的 flash_order 表，统一走 trade-service 的 orders 表(order_type=3)。
 */
@Slf4j
@Service
public class FlashSaleServiceImpl extends ServiceImpl<FlashSaleMapper, FlashSaleEntity> implements FlashSaleService {

    private final FlashSaleLuaScript luaScript;
    private final FlashSaleOrderPublisher publisher;
    private final OrderClient orderClient;

    public FlashSaleServiceImpl(FlashSaleLuaScript luaScript, FlashSaleOrderPublisher publisher,
                                 OrderClient orderClient) {
        this.luaScript = luaScript;
        this.publisher = publisher;
        this.orderClient = orderClient;
    }

    @Override
    public List<FlashSaleEntity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery().le(FlashSaleEntity::getStartTime, now)
                .ge(FlashSaleEntity::getEndTime, now)
                .eq(FlashSaleEntity::getStatus, FlashSaleStatusEnum.ACTIVE.getCode()).list();
    }

    @Override
    public FlashBuyResult buy(Long flashId, Long addressId) {
        FlashSaleEntity fs = getById(flashId);
        if (fs == null) throw new BizException(ErrorCode.FLASH_SALE_NOT_FOUND);
        if (fs.getStatus() != FlashSaleStatusEnum.ACTIVE.getCode())
            throw new BizException(ErrorCode.FLASH_SALE_NOT_STARTED);

        Long userId = UserContext.getUserId();

        // 1. Redis Lua 预扣（漏斗第一层 + 第二层）
        long remaining = luaScript.tryDeduct(flashId, userId, 1);
        if (remaining == -1) throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT);
        if (remaining == -2) throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED);
        if (remaining < 0) throw new BizException(ErrorCode.FLASH_SALE_FAILED);

        // 2. V4.1: 调用 trade-service 创建真实订单（漏斗第三层：订单落库）
        CreateOrderInternalReq req = new CreateOrderInternalReq();
        req.setUserId(userId);
        req.setSkuId(fs.getSkuId());
        req.setSellerId(0L); // flash_sale 表不存 sellerId，可从 SKU 查询
        req.setQuantity(1);
        req.setFlashPrice(fs.getFlashPrice());
        req.setFlashId(flashId);
        req.setAddressId(addressId);

        OrderSummaryDTO order;
        try {
            order = orderClient.createOrder(req);
            if (order == null) {
                throw new BizException(ErrorCode.FLASH_SALE_FAILED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 trade-service 创建秒杀订单失败: flashId={}, userId={}", flashId, userId, e);
            // 补偿：Redis 回滚库存
            luaScript.preloadStock(flashId, (int) remaining);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        }

        // 3. 异步发送 RocketMQ 消息（漏斗第四层：异步统计 + 补偿对账）
        FlashSaleOrderMessage msg = new FlashSaleOrderMessage();
        msg.setOrderNo(order.getOrderNo());
        msg.setFlashId(flashId);
        msg.setUserId(userId);
        msg.setSkuId(fs.getSkuId());
        msg.setQuantity(1);
        // 异步发送，失败不影响主流程（订单已创建）
        try {
            publisher.publish(msg);
        } catch (Exception e) {
            log.error("Flash MQ publish failed (non-critical): flashId={}, orderNo={}", flashId,
                    order.getOrderNo(), e);
        }

        FlashBuyResult result = new FlashBuyResult();
        result.setSuccess(true);
        result.setOrderNo(order.getOrderNo());
        result.setFlashId(flashId);
        result.setFlashPrice(fs.getFlashPrice());
        result.setProductId(fs.getProductId());
        result.setRemainingStock(remaining);
        return result;
    }
}
