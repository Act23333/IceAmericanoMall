package org.icedamericanomall.marketing.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.app.flash.FlashSaleManager;
import org.icedamericanomall.marketing.app.flash.FlashSaleViewManager;
import org.icedamericanomall.marketing.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.marketing.infrastructure.messaging.FlashSaleOrderPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 营销编排 Manager（应用层）—— 秒杀购买全链路。
 *
 * <p>职责边界：领域侧只做 Redis 预扣；本层负责跨服务编排（trade-service Feign 落单）
 * 与异步消息投递（RocketMQ 统计）—— Feign/MQ 只允许出现在应用层（见 09-constraints）。
 *
 * <pre>
 * POST /api/flash/buy 端到端时序:
 *   Controller → Manager.buyFlashSale
 *     1) FlashSaleManager.buy        Redis Lua 原子预扣 + 限购校验（含库存惰性兜底）
 *     2) OrderClient.createOrder     委托 trade-service 落真实订单，取回 orderNo
 *     3) FlashSaleOrderPublisher     投递 MQ 异步统计 soldCount（非关键路径，失败仅告警）
 *   ← FlashBuyVO { success, orderNo, flashId, flashPrice, remainingStock }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingManager {

    /**
     * 秒杀商品无店铺归属口径：秒杀为平台活动，订单 sellerId 固定 0（与 trade-service 约定）。
     */
    private static final long FLASH_SALE_NO_SELLER_ID = 0L;

    /** 单次秒杀购买件数（限购 1 件，与 {@code FLASH_SALE_LIMIT_EXCEEDED} 文案一致）。 */
    private static final int FLASH_PURCHASE_QUANTITY = 1;

    private final FlashSaleManager flashSaleManager;
    private final FlashSaleViewManager flashSaleViewManager;
    private final OrderClient orderClient;
    private final FlashSaleOrderPublisher publisher;
    private final MarketingConverter marketingConverter;

    /** 进行中的秒杀场次列表（商品名/主图/原价/进度百分比由视图装配层批量回填，fail-open）。 */
    public List<FlashSaleVO> listActiveFlashSales() {
        return flashSaleViewManager.toViewList(flashSaleManager.listActive());
    }

    /**
     * 秒杀购买全链路编排：预扣 → 落单 → 异步统计。
     *
     * @param flashId   秒杀活动 ID
     * @param addressId 收货地址 ID
     * @return 购买结果（含 trade-service 返回的真实 orderNo）
     * @throws BizException 售罄/超限购/落单失败等（错误码见 {@link ErrorCode}）
     */
    public FlashBuyVO buyFlashSale(Long flashId, Long addressId) {
        // 1. 领域操作：Redis Lua 原子预扣 + 限购校验
        FlashBuyResult preResult = flashSaleManager.buy(flashId, addressId);

        // 2. 应用层编排：Feign 委托 trade-service 创建真实订单
        Long userId = UserContext.getUserId();
        OrderSummaryCO order = createOrder(preResult, flashId, addressId, userId);
        preResult.setOrderNo(order.getOrderNo());

        // 3. 应用层编排：MQ 异步统计（非关键路径，失败不影响主流程）
        publishStats(preResult, flashId, userId, order.getOrderNo());
        return marketingConverter.toFlashBuyVO(preResult);
    }

    /** 调用 trade-service 落单；任何失败统一收敛为 {@code FLASH_SALE_FAILED}（预扣库存由超时/对账兜底）。 */
    private OrderSummaryCO createOrder(FlashBuyResult preResult, Long flashId, Long addressId, Long userId) {
        CreateOrderInternalReq req = new CreateOrderInternalReq();
        req.setUserId(userId);
        req.setSkuId(preResult.getSkuId());
        req.setSellerId(FLASH_SALE_NO_SELLER_ID);
        req.setQuantity(FLASH_PURCHASE_QUANTITY);
        req.setFlashPrice(preResult.getFlashPrice());
        req.setFlashId(flashId);
        req.setAddressId(addressId);
        try {
            OrderSummaryCO order = orderClient.createOrder(req);
            if (order == null) {
                throw new BizException(ErrorCode.FLASH_SALE_FAILED);
            }
            return order;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 trade-service 创建秒杀订单失败: flashId={}, userId={}", flashId, userId, e);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        }
    }

    /** 投递秒杀订单消息做异步统计；发布失败仅告警（非关键路径）。 */
    private void publishStats(FlashBuyResult preResult, Long flashId, Long userId, String orderNo) {
        FlashSaleOrderMessage msg = new FlashSaleOrderMessage();
        msg.setOrderNo(orderNo);
        msg.setFlashId(flashId);
        msg.setUserId(userId);
        msg.setSkuId(preResult.getSkuId());
        msg.setQuantity(FLASH_PURCHASE_QUANTITY);
        try {
            publisher.publish(msg);
        } catch (Exception e) {
            log.error("Flash MQ publish failed (non-critical): flashId={}, orderNo={}", flashId, orderNo, e);
        }
    }
}
