package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeHit;
import org.icedamericanomall.ai.domain.model.spu.SpuHit;
import org.icedamericanomall.ai.domain.port.VectorSearchPort;
import org.icedamericanomall.client.KnowledgeVectorClient;
import org.icedamericanomall.dto.KnowledgeVectorSearchCO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link VectorSearchPort} 的 Feign 实现（防腐层）——调用 search-service 内部向量端点。
 *
 * <p>取代原 {@code HttpVectorSearchAdapter}：后者用 {@code RestClient.create("http://search-service")}
 * 直连<b>服务名</b>，没有负载均衡/服务发现能力，在 Nacos 环境下无法解析（属于隐性故障）；
 * 改走 ia-api 的 Feign 客户端后获得负载均衡 + 熔断降级，且与其它跨服务调用统一入口。
 *
 * <p><b>降级语义：fail-open</b>。向量检索是对话增强能力，任何失败都按「无命中」处理
 * （返回空列表），绝不向上抛异常打断客服应答。熔断开启时由
 * {@code KnowledgeVectorClientFallback} 兜底；熔断关闭时由本类 try/catch 兜底（双保险）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignVectorSearchAdapter implements VectorSearchPort {

    private final KnowledgeVectorClient knowledgeVectorClient;

    @Override
    public List<SpuHit> vectorSearch(double[] embedding, int size, Long sellerId) {
        List<Map<String, Object>> raw = doSearch(embedding, size, sellerId, KnowledgeVectorClient.INDEX_PRODUCTS);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<SpuHit> hits = new ArrayList<>(raw.size());
        for (Map<String, Object> item : raw) {
            hits.add(SpuHitAssembler.fromMap(item));
        }
        return hits;
    }

    @Override
    public List<KnowledgeHit> searchKnowledge(double[] embedding, int size, Long sellerId) {
        List<Map<String, Object>> raw =
                doSearch(embedding, size, sellerId, KnowledgeVectorClient.INDEX_KNOWLEDGE_VECTORS);
        if (raw.isEmpty()) {
            return List.of();
        }
        List<KnowledgeHit> hits = new ArrayList<>(raw.size());
        for (Map<String, Object> item : raw) {
            hits.add(KnowledgeHitAssembler.fromMap(item));
        }
        return hits;
    }

    private List<Map<String, Object>> doSearch(double[] embedding, int size, Long sellerId, String index) {
        if (embedding == null || embedding.length == 0) {
            return List.of();
        }
        KnowledgeVectorSearchCO co = new KnowledgeVectorSearchCO();
        co.setEmbedding(toBoxedList(embedding));
        co.setSize(size);
        co.setSellerId(sellerId);
        co.setIndex(index);
        try {
            List<Map<String, Object>> response = knowledgeVectorClient.vectorSearch(co);
            return response == null ? List.of() : response;
        } catch (Exception e) {
            log.warn("向量检索失败（按无命中降级）: index={}, sellerId={}, cause={}",
                    index, sellerId, e.getMessage());
            return List.of();
        }
    }

    private static List<Double> toBoxedList(double[] embedding) {
        List<Double> boxed = new ArrayList<>(embedding.length);
        for (double value : embedding) {
            boxed.add(value);
        }
        return boxed;
    }
}
