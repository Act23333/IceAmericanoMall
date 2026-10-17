package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.FreightTemplateClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运费模板查询降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>，属 ADR-011 的
 * <b>② 判别型查询</b>、判据表的 <b>定价输入</b>。
 *
 * <p>本查询的返回值会被下单计价解释为业务事实（「商家无启用模板」→ {@code postFee = 0} 免邮）。
 * 若降级返回空集合，则 item-service 抖动会被伪装成「免邮」这一业务结论——平台资损且无告警，
 * 属典型的错误信息污染。调用方 {@code trade-service FreightPostageResolver} 亦按「加载失败
 * 不落缓存、异常原样上抛」实现（fail-closed 的另一半）。
 *
 * <p>分支：{@code category == CLIENT} → 原样抛 {@link UpstreamException}；
 * {@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}「暂时无法获取」。
 *
 * <p>⚠️ 与契约 §4.14.17 草拟口径（降级为空集合 → postFee=0）的偏差已在
 * {@link FreightTemplateClient} 类注释中登记。
 */
@Slf4j
@Component
public class FreightTemplateClientFallback implements FallbackFactory<FreightTemplateClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "运费模板暂时无法获取，请稍后重试";

    @Override
    public FreightTemplateClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new FreightTemplateClient() {

            @Override
            public List<FreightTemplateCO> query(FreightTemplateQueryCO query) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("运费模板查询降级(透传): 下游业务失败 sellerId={}, templateIds={}, code={}, msg={}",
                            query == null ? null : query.getSellerId(),
                            query == null ? null : query.getTemplateIds(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("运费模板查询降级: 暂时无法获取（category={}；禁止降级为空集合——"
                        + "「无模板」会被计价解释为免邮，属资损） sellerId={}, templateIds={}",
                        category, query == null ? null : query.getSellerId(),
                        query == null ? null : query.getTemplateIds(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
