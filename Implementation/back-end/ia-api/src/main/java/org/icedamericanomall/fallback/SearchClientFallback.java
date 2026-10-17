package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SearchClient;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 搜索服务降级工厂 —— <b>① 展示型查询（fail-soft），但 4xx 必须透传</b>。
 *
 * <p><b>为什么可以返回非成功 {@code Result} 而不是抛异常</b>：两个调用方都把
 * 「{@code data == null}」明确解释为「搜索服务不可用」——
 * <ul>
 *   <li>{@code ai.infrastructure.adapter.FeignSearchAdapter#searchSpus}：
 *       {@code if (result == null || result.getData() == null) throw new BizException(
 *       AI_SERVICE_UNAVAILABLE, "搜索服务暂不可用");}</li>
 *   <li>其上游 {@code SearchTool#searchSpus}（LangChain4j 工具）与
 *       {@code RAGRetriever#keywordRecall}（关键词召回）均为
 *       {@code catch (Exception) → 友好文案 / 空列表}。</li>
 * </ul>
 * 搜索是 AI 助手的「锦上添花」能力：降级后助手仍可基于其它工具回答，故返回
 * {@link Result#error(int, String)}（而非 {@code Result.ok(null)} —— 后者会谎报
 * 「成功但结果为空」）保留 {@code code/message}，任何未来检查 {@code code} 的调用方
 * 也能正确识别故障。
 *
 * <p><b>为什么仍然要分流 {@code CLIENT}</b>：上面「返回非成功 Result」只对
 * <b>TRANSIENT / BUG</b>（服务挂了 / 超时 / 熔断 / 500）成立 —— 那时「搜索暂时不可用」
 * 是事实。但若下游返回的是 <b>4xx + 已知 code</b>（关键词非法、请求被契约拒绝），
 * 把「你参数错了」改写成「搜索服务不可用」就是 {@code collaboration/09-constraints.md}
 * §三·八·1 明令禁止的<b>错误信息污染</b>：调用方会去重试、去降级、去找运维，
 * 而真正该改的是请求本身。故：
 * <ul>
 *   <li>{@code category == CLIENT && upstream != null} → <b>原样抛
 *       {@link UpstreamException}</b>（保留下游 code/文案/traceId）。
 *       安全前提已核实：{@code FeignSearchAdapter} 只判 {@code data == null} 不 catch，
 *       异常最终由 {@code SearchTool} / {@code RAGRetriever} 的
 *       {@code catch (Exception)} 收口 —— 与今天「返回 error Result」的<b>外部行为一致</b>
 *       （上一轮对话不中断），但日志与链路里留下了真实原因；</li>
 *   <li>其余（{@code TRANSIENT} / {@code BUG}，无 {@code upstream} 可透传）→ 仍返回
 *       非成功 {@code Result}，<b>返回类型不变</b>（{@code Result<Map<String,Object>>}）。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SearchClientFallback implements FallbackFactory<SearchClient> {

    /** 搜索降级文案（展示型：措辞为「暂时不可用/稍后重试」，不得谎报成功）。 */
    private static final String DEGRADED_MESSAGE = "搜索暂时不可用，请稍后重试";

    @Override
    public SearchClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new SearchClient() {

            @Override
            public Result<Map<String, Object>> searchSpus(String keyword, int size) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    // 4xx 是调用方的错（参数/契约），改写成本域 SYSTEM_BUSY 就是错误信息污染。
                    log.warn("搜索服务降级(透传): 下游业务失败 keyword={}, size={}, code={}, msg={}",
                            keyword, size, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("搜索服务降级: 暂时不可用，本次降级为空结果（① 展示型，调用方按 data==null 判故障）"
                        + " keyword={}, size={}, category={}", keyword, size, category, cause);
                return Result.error(ErrorCode.SYSTEM_BUSY.getCode(), DEGRADED_MESSAGE);
            }
        };
    }
}
