package org.icedamericanomall.trade.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.trade.domain.enums.AfterSaleStatusEnum;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.springframework.stereotype.Component;

/**
 * trade 买家触达协作器（ADR-042）—— 包裹发货 / 售后审核结果两事件的买家站内信单出口。
 *
 * <p><b>职责</b>：封装 {@link NotificationClient#sendInAppMessage} 调用、type 常量与 fail-open
 * 样板（发送失败仅 log.warn，不重试、不回滚，绝不影响发货/审核主流程），订单与售后两个用例族
 * 共用一个协作器，避免样板重复。
 *
 * <p><b>事务纪律</b>：两个触发点（{@code OrderLifecycleManager#shipOrder} 物流记录创建之后、
 * {@code AfterSaleManager#review} 落库之后）均在本地事务外（Feign 严禁进事务，硬约束）。
 *
 * <p><b>sender 口径</b>：系统通知 senderId 置空，senderName/avatar 置空串由前端渲染
 * （对齐 item {@code ReviewManager} 先例，不查用户名）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventNotifier {

    private static final String NOTIFY_TYPE_ORDER_SHIPPED = "ORDER_SHIPPED";
    private static final String NOTIFY_TYPE_AFTER_SALE_REVIEWED = "AFTER_SALE_REVIEWED";
    private static final String NOTIFY_TITLE_ORDER_SHIPPED = "发货通知";
    private static final String NOTIFY_TITLE_AFTER_SALE_REVIEWED = "售后审核结果通知";
    /** 买家订单链接前缀（前端路由），四事件统一口径。 */
    private static final String ORDER_LINK_PREFIX = "/orders/";

    private final NotificationClient notificationClient;

    /**
     * 包裹发货买家触达（ADR-042）—— 站内信携带订单号 + 包裹号。
     *
     * @param order     发货订单聚合（含 userId / orderNo）；订单或收件人缺失时跳过
     * @param packageNo 本次发货包裹号（{@code ShipPackageResult#packageNo}）
     */
    public void notifyOrderShipped(Order order, int packageNo) {
        if (order == null || order.getUserId() == null) {
            log.warn("[trade-notify][skip] 发货买家触达跳过（订单或收件人缺失）: orderNo={}",
                    order == null ? null : order.getOrderNo());
            return;
        }
        sendInApp(order.getUserId(), NOTIFY_TYPE_ORDER_SHIPPED, NOTIFY_TITLE_ORDER_SHIPPED,
                "您的订单 " + order.getOrderNo() + " 第 " + packageNo + " 个包裹已发货，请留意物流动态",
                ORDER_LINK_PREFIX + order.getOrderNo());
    }

    /**
     * 售后审核结果买家触达（ADR-042）—— 站内信携带同意/拒绝结论 + 审核备注。
     *
     * @param afterSale   已落库的售后单聚合（status 已置为目标结论，含 userId / orderNo）；
     *                    收件人缺失时跳过
     * @param adminRemark 审核备注（可空）
     */
    public void notifyAfterSaleReviewed(AfterSale afterSale, String adminRemark) {
        if (afterSale == null || afterSale.getUserId() == null) {
            log.warn("[trade-notify][skip] 售后审核触达跳过（售后单或收件人缺失）: orderNo={}",
                    afterSale == null ? null : afterSale.getOrderNo());
            return;
        }
        String remarkText = (adminRemark == null || adminRemark.isBlank()) ? ""
                : "，审核备注：" + adminRemark;
        sendInApp(afterSale.getUserId(), NOTIFY_TYPE_AFTER_SALE_REVIEWED,
                NOTIFY_TITLE_AFTER_SALE_REVIEWED,
                "您的订单 " + afterSale.getOrderNo() + " 的售后申请已审核完成，结论："
                        + conclusionOf(afterSale.getStatus()) + remarkText,
                ORDER_LINK_PREFIX + afterSale.getOrderNo());
    }

    /** 统一发送（fail-open）：任何异常仅告警，不外溢。 */
    private void sendInApp(Long recipientId, String type, String title, String content, String linkUrl) {
        try {
            InAppMessageSendReqCO message = new InAppMessageSendReqCO();
            message.setRecipientId(recipientId);
            // 系统通知：senderId 置空；senderName/avatar 置空串由前端按类型渲染（不查用户名）
            message.setSenderName("");
            message.setSenderAvatar("");
            message.setType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setLinkUrl(linkUrl);
            notificationClient.sendInAppMessage(message);
        } catch (Exception e) {
            log.warn("发送买家触达失败(type={}, recipient={}): {}", type, recipientId, e.getMessage());
        }
    }

    /** 审核结论文案（状态枚举描述：同意/拒绝/…）；非法状态降级为「已处理」。 */
    private static String conclusionOf(Integer status) {
        AfterSaleStatusEnum statusEnum = AfterSaleStatusEnum.of(status);
        return statusEnum == null ? "已处理" : statusEnum.getDesc();
    }
}
