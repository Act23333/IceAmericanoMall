package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生效促销查询降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>，属 ADR-011 的 <b>② 判别型查询</b>。
 *
 * <p>返回值会被定价中心解释为业务事实（「无生效促销」→ 不计促销优惠）。若降级返回空集合，
 * marketing 抖动会被伪装成「无促销」——用户应得优惠被静默吞掉，且与促销配置口径产生价格
 * 不一致。因此降级必须抛异常，使下单 fail-closed（不阻断重试，但不静默吞优惠）。
 *
 * <p><b>分支（{@code 09-constraints} §三·八·1 透传优先）</b>：
 * <ul>
 *   <li>{@code category == CLIENT} 且异常链中带已类型化的下游错误 → <b>原样抛
 *       {@link UpstreamException}</b>（下游 4xx + 已知 {@code code} 是<b>业务事实</b>，
 *       如「店铺参数非法」；说成「促销计价服务暂不可用」会把它伪装成可重试的可用性问题）；</li>
 *   <li>{@code TRANSIENT} / {@code BUG} → <b>原有 fail-closed 口径一字不变</b>：
 *       {@code BizException(SYSTEM_BUSY)}（503 / {@code TRANSIENT} / 可重试）。</li>
 * </ul>
 * 两条分支都仍然是「抛异常」—— <b>绝无</b>「降级成空集合」的路径（否则用户多付钱）。
 *
 * <p><b>透传 4xx 不会让调用方误判（已核实）</b>：
 * <ul>
 *   <li>trade 定价中心 {@code PricingService#resolvePromotionDiscount} 刻意不捕获 ——
 *       异常使下单失败（不会拿错价下单）；</li>
 *   <li>item 详情页展示位 {@code SpuPromotionManager#loadTags} 以 {@code catch (Exception)}
 *       包住、降级为空标签（展示层 fail-open，详情页照常渲染）。</li>
 * </ul>
 *
 * <p>定类依据与实现先例：{@code FreightTemplateClientFallback}（I43，ADR-037）。
 */
@Slf4j
@Component
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new PromotionClient() {

            @Override
            public List<PromotionCO> queryActive(PromotionQueryCO query) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("生效促销查询降级(透传): 下游业务失败 sellerId={}, code={}, msg={}",
                            query == null ? null : query.getSellerId(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("生效促销查询降级：marketing-service 不可用（禁止降级为空集合，"
                                + "用户应得优惠会被静默吞掉） sellerId={}",
                        query == null ? null : query.getSellerId(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "促销计价服务暂不可用，请稍后重试");
            }
        };
    }
}
