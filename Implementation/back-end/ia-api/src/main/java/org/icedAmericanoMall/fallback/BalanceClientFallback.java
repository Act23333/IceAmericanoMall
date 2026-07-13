package org.icedAmericanoMall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.BalanceClient;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BalanceClientFallback implements FallbackFactory<BalanceClient> {
    @Override
    public BalanceClient create(Throwable cause) {
        return (userId, amount) -> {
            log.error("余额扣减失败（服务不可用）: userId={}, amount={}", userId, amount, cause);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "余额服务暂不可用");
        };
    }
}
