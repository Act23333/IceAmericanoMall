package org.icedamericanomall.search.domain.gateway;

import java.util.List;

/**
 * 商品名联想网关（领域端口）—— SE5 搜索联想「商品名前缀源」的读取契约。
 *
 * <p><b>选型说明</b>：商品名源恒走 DB LIKE 前缀查询（{@code name LIKE 'prefix%'}），
 * 不依赖 ES —— 联想必须「ES 关闭可用」（契约 SE5），且两条实现链（DB/ES）共用同一数据源，
 * 避免按 ES 开关分裂两套联想口径。实现方：{@code infrastructure.adapter.ItemServiceSpuSuggestGateway}（item 域检索端点，ADR-052）。
 */
public interface SpuSuggestGateway {

    /**
     * 商品名前缀联想（SE5 源 2）—— 在售商品的名称前缀命中。
     *
     * @param prefix 名称前缀（调用方保证非空白）
     * @param limit  返回条数上限
     * @return 商品名列表（同名商品去重，按销量降序取热门优先）；无命中为空列表
     */
    List<String> suggestNames(String prefix, int limit);
}
