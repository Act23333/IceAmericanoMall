package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.fallback.ItemSearchClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for item-service internal spu-search endpoints（ADR-052 数据面归属）。
 *
 * <p><b>职责边界</b>：{@code spu}/{@code sku} 表的唯一所有者是 item-service——
 * search-service 的 LIKE 降级检索、联想词、ES 重建数据源、publishTime 回填，
 * 一律经此出口获取，禁止任何形式的直读建库（ADR-052 受控豁免已随本 client 落地退出）。
 *
 * <p><b>降级语义：fail-closed（空结果，不抛死）</b>。检索是 C 端关键路径，
 * item-service 不可用时：
 * <ul>
 *   <li>{@link #searchPage} / {@link #exportPage} 降级返回空页（等价「无命中」/「无数据可重建」）；</li>
 *   <li>{@link #suggest} 降级返回空列表（联想位可空，输入框不阻断）；</li>
 *   <li>{@link #detail} 降级返回 {@code null}（publishTime 回填按缺失处理，fail-open）。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方（search-service）需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}，
 * 且降级工厂已在 {@link DefaultFeignConfig} 显式登记。
 */
@FeignClient(
    name = "item-service",
    path = "/internal/item/search",
    contextId = "itemSearch",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = ItemSearchClientFallback.class
)
public interface ItemSearchClient {

    /**
     * 商品检索分页（LIKE 降级链路数据源）—— 语义与 item 域 SQL 实现一致：
     * 在售商品 + 关键词 LIKE（name/description）+ 分类/品牌过滤 + 在售 SKU 最低价区间 + 白名单排序。
     */
    @PostMapping("/page")
    SpuSearchPageCO searchPage(@RequestBody SpuSearchQueryCO query);

    /** 商品名前缀联想（SE5 源 2）：在售商品名称前缀命中，销量降序，同名去重后截断。 */
    @GetMapping("/suggest")
    List<String> suggest(@RequestParam("prefix") String prefix, @RequestParam("limit") int limit);

    /** ES 重建数据源：在售商品全量导出（按 id 升序分页，页长由调用方控制）。 */
    @GetMapping("/export")
    SpuSearchPageCO exportPage(@RequestParam("page") int page, @RequestParam("size") int size);

    /**
     * 单商品检索条目；不存在返回 {@code null}。
     *
     * <p><b>入参单形态（ADR-062 §决策 1/4，契约 §1.9③）</b>：{@code {spuId}} 只接受
     * <b>SPU 业务键</b> 32-hex（{@code spu.spu_id}，走唯一索引 {@code uk_spu_id}），
     * 原「纯数字 = 技术键」的双形态分支<b>已删除</b>；形态非法 → 400 {@code PARAM_ERROR}。
     * 同一条查询顺带带回 {@code publishTime}，故商品向量摄取（I47）的
     * 「业务键 → 技术键」解析与 publishTime 回填合并为<b>一次</b>往返。
     *
     * <p>本端点<b>不新增路径</b>：沿用既有 {@code /detail/{spuId}} 路由，仅路径变量改名
     * （ADR-062 §决策 2 表 A #22；端点对账保持 287/0/43，新增路径属契约外变更）。
     */
    @GetMapping("/detail/{spuId}")
    SpuSearchItemCO detail(@PathVariable("spuId") String spuId);
}
