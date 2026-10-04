package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.product.ProductHit;
import org.icedamericanomall.ai.domain.port.SearchPort;
import org.icedamericanomall.client.SearchClient;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchPort} 的 Feign 实现（防腐层）——把 search-service 的 Map 报文转成本上下文领域对象。
 */
@Component
@RequiredArgsConstructor
public class FeignSearchAdapter implements SearchPort {

    private final SearchClient searchClient;

    @Override
    public List<ProductHit> searchProducts(String keyword, int size) {
        Result<Map<String, Object>> result = searchClient.searchProducts(keyword, size);
        if (result == null || result.getData() == null) {
            // SearchClient 降级为 fail-soft（data 为 null）→ 视为搜索服务不可用
            throw new BizException(ErrorCode.AI_SERVICE_UNAVAILABLE, "搜索服务暂不可用");
        }
        Object rawRecords = result.getData().get("records");
        if (!(rawRecords instanceof List<?> records) || records.isEmpty()) {
            return List.of();
        }
        List<ProductHit> hits = new ArrayList<>(records.size());
        for (Object item : records) {
            if (item instanceof Map<?, ?> map) {
                hits.add(ProductHitAssembler.fromMap(map));
            }
        }
        return hits;
    }
}
