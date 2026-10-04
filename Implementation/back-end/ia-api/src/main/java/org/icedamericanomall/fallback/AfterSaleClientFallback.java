package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AfterSaleClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 售后服务（trade-service）降级工厂 —— <b>fail-open + 告警（不抛异常）</b>。
 *
 * <p>覆盖的调用只有 {@code markRefunded}（I42）：它是<b>退款成功后的下游状态回写</b>
 * （ADR-011 三分法中的 ① 类）。静默丢弃的后果有限且可收敛——售后单暂时停在 {@code APPROVED(2)}，
 * 由对账 / 管理端重试补写；而若在此 fail-closed，会让一次<b>已经成功</b>的退款在 pay-service 侧
 * 被当成失败处理，制造「钱已退、状态被篡改」的更严重不一致。故按 fail-open 处理并告警。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}。
 */
@Slf4j
@Component
public class AfterSaleClientFallback implements FallbackFactory<AfterSaleClient> {

    @Override
    public AfterSaleClient create(Throwable cause) {
        return afterSaleId -> log.warn("售后状态回写降级（fail-open）：markRefunded 丢失，"
                + "售后单将停留 APPROVED 待对账/重试收敛 afterSaleId={}", afterSaleId, cause);
    }
}
