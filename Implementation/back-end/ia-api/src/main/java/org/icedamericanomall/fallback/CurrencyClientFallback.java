package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CurrencyClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 币种当前汇率降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错），不再伪造汇率</b>。
 *
 * <p><b>改前缺陷（本批 C-1 修复）</b>：降级返回哨兵 {@code new CurrencyRateCO(code, -1L, null)}
 * —— 即「凭空造出一条汇率记录」。虽然唯一调用方
 * {@code trade-service OrderDisplaySnapshotService} 有 {@code rateScaled < 1} 的有效性守卫，
 * 把 -1 当作「无汇率」跳过，但这是<b>靠下游再校验兜住上游的谎报</b>：
 * <ul>
 *   <li>任何新增消费方（或未来「用返回值直接换算」的代码）都会把 -1 当成一条真实数据；</li>
 *   <li>凡是「汇率」都属<b>定价输入</b>（PRD §十七 / 判据表），凭空的汇率一旦参与换算就是错价下单，
 *       这类值<b>不许由降级工厂生产</b>；</li>
 *   <li>日志把故障写成「订单跳过展示快照（主流程零影响）」，掩盖了 wallet-service 的可用性问题。</li>
 * </ul>
 *
 * <p><b>改成 fail-closed 后调用方行为不变（已实测）</b>：
 * {@code OrderDisplaySnapshotService#queryQuietly} 用 {@code catch (Exception)} 把任何异常降级为
 * {@code null} 并跳过快照（PRD §17.7.2「汇率失败绝不阻塞交易主流程」），
 * 因此本工厂抛错与该守卫<b>结果同义</b>（订单两列 NULL、订单照常创建），
 * 只是把「谎报一条 -1 汇率」换成「如实报告拿不到」。
 *
 * <p>分支：{@code category == CLIENT}（如 1103 币种未启用）→ 原样抛 {@link UpstreamException}；
 * {@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}，文案「暂时无法获取」。
 */
@Slf4j
@Component
public class CurrencyClientFallback implements FallbackFactory<CurrencyClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "汇率暂时无法获取，请稍后重试";

    @Override
    public CurrencyClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new CurrencyClient() {

            @Override
            public CurrencyRateCO getCurrent(String code) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("币种汇率查询降级(透传): 下游业务失败 currencyCode={}, upstreamStatus={}, downstreamCode={}, category={}, msg={}",
                            code, upstream.getUpstreamStatus(), upstream.getCode(), category, upstream.getMsg());
                    throw upstream;
                }
                log.error("币种汇率查询降级: 暂时无法获取（category={}；禁止伪造汇率——定价输入不得由降级"
                        + "工厂生产；调用方 OrderDisplaySnapshotService 会跳过展示快照） code={}",
                        category, code, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
