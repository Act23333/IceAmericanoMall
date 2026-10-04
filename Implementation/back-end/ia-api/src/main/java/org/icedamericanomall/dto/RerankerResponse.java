package org.icedamericanomall.dto;

import java.util.List;

/**
 * BGE-reranker（重排序模型）响应 —— Java Record。
 *
 * <p><b>契约形状（V5.3 仅补说明，不改类型）</b>：调用方 ai-service 的
 * {@code RerankerService} 用 {@code RestClient} 把重排服务（FastAPI）的
 * {@code POST /rerank} 响应<b>直接绑定到本 record</b>，因此 wire 契约就是：
 * {@code {"results":[{"index":0,"score":0.93,"text":"..."}]}}。
 * 三个字段都参与 JSON 绑定：{@code index}（候选序号）、{@code score}（相关性分数）、
 * {@code text}（原文）。服务端字段改名/改层级会直接导致绑定失败并走启发式降级
 * （{@code RerankerService} 的 catch 分支），属于<b>弱类型契约</b> ——
 * 强类型化（{@code @JsonProperty} 显式映射 + 契约测试）需要 ai-service 同批改造，
 * 本次审计不改类型。
 *
 * <p>{@code results} 允许为 {@code null}（模型无召回时），调用方须判空。
 */
public record RerankerResponse(List<RerankedDocument> results) {
    public record RerankedDocument(int index, double score, String text) {}
}
