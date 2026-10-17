package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品检索（item-service 内部端点）降级工厂 —— <b>逐方法定类</b>（ADR-052）。
 *
 * <p>分支纪律：{@code category == CLIENT} 的 4xx 一律原样抛 {@link UpstreamException}；
 * {@code TRANSIENT} / {@code BUG} 按下表处置：
 * <table border="1">
 *   <caption>逐方法降级语义（依据=读调用方所得）</caption>
 *   <tr><th>方法</th><th>调用方</th><th>定类</th><th>降级行为</th></tr>
 *   <tr><td>{@code searchPage}</td>
 *       <td>{@code search-service ItemServiceSpuSearchGateway}（ES 关闭时的 LIKE 检索链）</td>
 *       <td>① 展示型</td>
 *       <td>空页：C 端「无命中」是合法展示结果（ADR-052），调用方以 {@code page == null ||
 *           records == null} 判空并展示空列表</td></tr>
 *   <tr><td>{@code suggest}</td><td>{@code ItemServiceSpuSuggestGateway}（联想词）</td>
 *       <td>① 展示型</td><td>空列表 = 无联想</td></tr>
 *   <tr><td>{@code detail}</td><td>{@code EsSpuSearchGateway#resolveByBusinessKey}</td>
 *       <td>① 展示型</td><td>{@code null} = 条目缺失（ADR-061 §决策 2，调用方按「商品不存在」处理）</td></tr>
 *   <tr><td><b>{@code exportPage}</b></td>
 *       <td>{@code EsSpuSearchGateway#fetchOnSaleSpus}（<b>reindex 全量数据源</b>）</td>
 *       <td><b>② 判别型</b></td>
 *       <td><b>fail-closed</b>：空页不是「无数据」而是「拿不到数据」。
 *           该循环以「单页为空即终止」为出口，降级返回空页会让<b>重建把索引刷成 0 条文档</b>
 *           （静默成功 = 更糟的破坏），故必须抛错让 reindex 显式失败</td></tr>
 * </table>
 */
@Slf4j
@Component
public class ItemSearchClientFallback implements FallbackFactory<ItemSearchClient> {

    /** exportPage（reindex 数据源）在 TRANSIENT/BUG 路径的降级文案。 */
    private static final String EXPORT_DEGRADED_MESSAGE = "商品数据暂时无法获取，重建已中止";
    /** detail 在 TRANSIENT/BUG 路径的提示（返回值语义为「条目缺失」）。 */
    private static final String DETAIL_DEGRADED_HINT = "商品条目查询降级：暂时无法获取";

    @Override
    public ItemSearchClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new ItemSearchClient() {

            @Override
            public SpuSearchPageCO searchPage(SpuSearchQueryCO query) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("商品检索降级(透传): 下游业务失败 keyword={}, code={}, msg={}",
                            query == null ? null : query.getKeyword(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("商品检索降级: 暂时无法获取，本次按空结果展示（① 展示型，ADR-052）"
                        + " keyword={}, category={}", query == null ? null : query.getKeyword(), category, cause);
                return SpuSearchPageCO.empty();
            }

            @Override
            public List<String> suggest(String prefix, int limit) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("商品联想降级(透传): 下游业务失败 prefix={}, code={}, msg={}",
                            prefix, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("商品联想降级: 暂时无法获取，本次按无联想处理（① 展示型）"
                        + " prefix={}, category={}", prefix, category, cause);
                return List.of();
            }

            @Override
            public SpuSearchPageCO exportPage(int page, int size) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("商品导出降级(透传): 下游业务失败 page={}, size={}, code={}, msg={}",
                            page, size, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("商品导出降级: 暂时无法获取（category={}；② 判别型——空页会让 reindex 把索引"
                        + "重建为 0 条文档，故必须 fail-closed） page={}, size={}", category, page, size, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, EXPORT_DEGRADED_MESSAGE);
            }

            @Override
            public SpuSearchItemCO detail(String id) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("商品条目查询降级(透传): 下游业务失败 id={}, code={}, msg={}",
                            id, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("{}（① 展示型，fail-open）：id={} 按缺失处理 category={}",
                        DETAIL_DEGRADED_HINT, id, category, cause);
                return null;
            }
        };
    }
}
