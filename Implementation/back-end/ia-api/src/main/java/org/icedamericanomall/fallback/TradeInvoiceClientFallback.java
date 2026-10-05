package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 全额退款发票联动降级工厂 —— <b>fail-open + 告警</b>（触发器语义，见 {@link TradeInvoiceClient} 类注释）。
 */
@Slf4j
@Component
public class TradeInvoiceClientFallback implements FallbackFactory<TradeInvoiceClient> {

    @Override
    public TradeInvoiceClient create(Throwable cause) {
        return new TradeInvoiceClient() {

            @Override
            public void voidOnFullRefund(InvoiceVoidCO invoiceVoid) {
                log.warn("全额退款发票联动降级：trade-service 不可用，发票未联动冲红/作废"
                                + "（管理端筛选人工收敛，PRD D4 已知风险） orderNo={}, sceneRef={}",
                        invoiceVoid == null ? null : invoiceVoid.getOrderNo(),
                        invoiceVoid == null ? null : invoiceVoid.getSceneRef(), cause);
            }
        };
    }
}
