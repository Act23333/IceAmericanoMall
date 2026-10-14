package org.icedamericanomall.search.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ItemSearchClient;
import org.icedamericanomall.search.domain.gateway.ProductSuggestGateway;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品名联想网关 —— item-service 内部检索端点实现（ADR-052：原 {@code ProductSuggestDbGateway}
 * 直读 {@code product} 表的前缀 LIKE 已退出，数据面归 item 域）。
 *
 * <p>前缀锚定 / 在售过滤 / 销量降序 / 同名去重均由 item 侧承载
 * （{@code ProductRepository#suggestNames}，SQL 语义与原直读实现一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemServiceProductSuggestGateway implements ProductSuggestGateway {

    private final ItemSearchClient itemSearchClient;

    @Override
    public List<String> suggestNames(String prefix, int limit) {
        List<String> names = itemSearchClient.suggest(prefix, limit);
        return names == null ? List.of() : names;
    }
}
