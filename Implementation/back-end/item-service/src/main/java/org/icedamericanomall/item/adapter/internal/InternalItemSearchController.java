package org.icedamericanomall.item.adapter.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.item.app.search.ItemSearchQueryManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品检索内部端点（ADR-052：search-service 数据面出口）—— 仅在集群内经 Feign 调用
 * （ia-api {@code ItemSearchClient}），内部端点不做 Result 包装。
 *
 * <p>消费方：search-service 的 LIKE 降级检索 / 联想词 / ES 重建数据源 / publishTime 回填。
 */
@Slf4j
@RestController
@RequestMapping("/internal/item/search")
@RequiredArgsConstructor
public class InternalItemSearchController {

    private final ItemSearchQueryManager itemSearchQueryManager;

    /** 商品检索分页（LIKE 降级链路数据源）。 */
    @PostMapping("/page")
    public SpuSearchPageCO searchPage(@RequestBody SpuSearchQueryCO query) {
        return itemSearchQueryManager.searchPage(query);
    }

    /** 商品名前缀联想。 */
    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam("prefix") String prefix, @RequestParam("limit") int limit) {
        return itemSearchQueryManager.suggest(prefix, limit);
    }

    /** 在售商品全量导出分页（ES 重建数据源）。 */
    @GetMapping("/export")
    public SpuSearchPageCO exportPage(@RequestParam("page") int page, @RequestParam("size") int size) {
        return itemSearchQueryManager.exportPage(page, size);
    }

    /**
     * 单商品检索卡片；不存在返回 null 体。
     *
     * <p><b>入参既接受技术键也接受业务键</b>（ADR-061 §决策 2）：纯数字按 {@code spu.id} 查，
     * 32-hex 按 {@code spu.spu_id} 查。消费方：search-service 的 publishTime 回填（技术键）
     * 与商品向量摄取/相似检索的锚点解析（业务键）。<b>不新增路由</b>（端点对账保持 287/0/43）。
     */
    @GetMapping("/detail/{id}")
    public SpuSearchItemCO detail(@PathVariable("id") String id) {
        return itemSearchQueryManager.detail(id);
    }
}
