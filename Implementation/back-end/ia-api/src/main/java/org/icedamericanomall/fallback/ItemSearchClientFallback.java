package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.dto.ProductSearchItemCO;
import org.icedamericanomall.dto.ProductSearchPageCO;
import org.icedamericanomall.dto.ProductSearchQueryCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品检索（item-service 内部端点）降级工厂 —— <b>fail-closed（空结果，不抛异常）</b>，ADR-052。
 *
 * <p>为什么是 fail-closed：检索是 C 端关键路径，降级返回空结果 ≠ 放行/错误数据——
 * 与「无命中」同义，前端展示空列表；ES 链路正常时该降级根本不会触发
 * （本 client 只服务 LIKE 降级链与 ES 重建，非 ES 主检索链路）。
 */
@Slf4j
@Component
public class ItemSearchClientFallback implements FallbackFactory<ItemSearchClient> {

    @Override
    public ItemSearchClient create(Throwable cause) {
        return new ItemSearchClient() {

            @Override
            public ProductSearchPageCO searchPage(ProductSearchQueryCO query) {
                log.warn("商品检索降级：item-service 不可用，本次按空结果处理 keyword={}",
                        query == null ? null : query.getKeyword(), cause);
                return ProductSearchPageCO.empty();
            }

            @Override
            public List<String> suggest(String prefix, int limit) {
                log.warn("商品联想降级：item-service 不可用，本次按无联想处理 prefix={}", prefix, cause);
                return List.of();
            }

            @Override
            public ProductSearchPageCO exportPage(int page, int size) {
                log.warn("商品导出降级：item-service 不可用，page={} 按空页处理（本次重建无数据）", page, cause);
                return ProductSearchPageCO.empty();
            }

            @Override
            public ProductSearchItemCO detail(Long id) {
                log.warn("商品条目查询降级：item-service 不可用，id={} 按缺失处理（fail-open）", id, cause);
                return null;
            }
        };
    }
}
