package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.fallback.SearchClientFallback;
import org.icedamericanomall.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for search-service spu search endpoints.
 * Used by ai-service SearchTool for Agent-triggered spu queries.
 * <p>
 * Returns {@code Result<Map>} because search-service response wraps MyBatis-Plus
 * {@code Page<SpuSearchVO>} which is deserialized as a Map with "records" key.
 *
 * <p>降级语义：<b>fail-soft</b>（返回非成功 {@code Result}，{@code data} 为 null）。
 * 调用方把 {@code data == null} 解释为「搜索服务暂不可用」，与下游故障语义一致，
 * 且搜索是 AI 助手的增强能力，不应因降级中断整轮对话。
 * 详见 {@link SearchClientFallback}。
 *
 * <p><b>契约形状（V5.3 补充说明，暂不改类型）</b>：{@code Result.data} 是
 * MyBatis-Plus 分页对象的原始 JSON 影子，字段形状为
 * {@code {"records":[{...}], "total":n, "size":n, "current":n, "pages":n}}，
 * 其中 {@code records} 的元素字段随 search-service 的 {@code SpuSearchVO} 演进 ——
 * 属于<b>弱类型契约</b>。之所以不改为强类型 CO：本次改动只做审计修补，
 * 强类型化需要 search-service 同批发布（否则字段漂移会在运行期变成解析失败）。
 * 调用方必须做 {@code null}/缺键防御，禁止直接强转。
 *
 * <p><b>已知偏差（待迁移，V5.3 审计记录）</b>：本客户端走的是 search-service 的
 * <b>公开契约</b> {@code path = "/api/search"}，集群内调用应统一走 {@code /internal/**}
 * （见 ADR-004）。迁移需要 search-service 暴露内部端点，超出 ia-api 的改动范围，
 * 本次仅登记偏差。
 */
@FeignClient(
    name = "search-service",
    path = "/api/search",
    contextId = "search",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = SearchClientFallback.class
)
public interface SearchClient {

    /**
     * 商品搜索（关键词 + 分页）。
     * <p>
     * 对应 search-service {@code SearchController#search}，路径段 {@code /spu}（检索对象是 SPU）。
     *
     * <p><b>2026-10-18 修正</b>：这里原本写 {@code /product}，而 ADR-062 把资源段改名为 {@code /spu}
     * 时**漏改了这个消费者** ⇒ 调用必 404，被 fail-soft 降级掩盖成"搜索服务暂不可用"。
     * 教训：改名类决策必须连同"消费方清单"一起改，否则断点会被降级吞掉。
     *
     * @param keyword  搜索关键词
     * @param size     返回条数（默认 5）
     * @return Result.data 为包含 "records" 列表的 Map
     */
    @GetMapping("/spu")
    Result<Map<String, Object>> searchSpus(
        @RequestParam("keyword") String keyword,
        @RequestParam(value = "size", defaultValue = "5") int size
    );
}
