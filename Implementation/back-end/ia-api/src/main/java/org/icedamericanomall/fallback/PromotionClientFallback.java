package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生效促销查询降级工厂 —— <b>fail-closed（抛异常）</b>，属 ADR-011 的 <b>② 判别型查询</b>。
 *
 * <p>返回值会被定价中心解释为业务事实（「无生效促销」→ 不计促销优惠）。若降级返回空集合，
 * marketing 抖动会被伪装成「无促销」——用户应得优惠被静默吞掉，且与促销配置口径产生价格
 * 不一致。因此降级必须抛异常，使下单 fail-closed（不阻断重试，但不静默吞优惠）。
 *
 * <p>定类依据与实现先例：{@code FreightTemplateClientFallback}（I43，ADR-037）。
 */
@Slf4j
@Component
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        return new PromotionClient() {

            @Override
            public List<PromotionCO> queryActive(PromotionQueryCO query) {
                log.error("生效促销查询降级：marketing-service 不可用（禁止降级为空集合，"
                                + "用户应得优惠会被静默吞掉） sellerId={}",
                        query == null ? null : query.getSellerId(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "促销计价服务暂不可用，请稍后重试");
            }
        };
    }
}
