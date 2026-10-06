package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 币种当前汇率降级工厂 —— <b>fail-open 哨兵</b>（PRD §17.7.2：快照失败跳过、订单照常创建）。
 */
@Slf4j
@Component
public class CurrencyClientFallback implements FallbackFactory<CurrencyClient> {

    /** 汇率降级哨兵（rateScaled=-1，trade 侧据此跳过快照）。 */
    public static final long RATE_FALLBACK_SENTINEL = -1L;

    @Override
    public CurrencyClient create(Throwable cause) {
        return new CurrencyClient() {

            @Override
            public CurrencyRateCO getCurrent(String code) {
                log.warn("币种汇率查询降级：wallet-service 不可用，订单跳过展示快照（NULL=CNY，主流程零影响）"
                        + " code={}", code, cause);
                return new CurrencyRateCO(code, RATE_FALLBACK_SENTINEL, null);
            }
        };
    }
}
