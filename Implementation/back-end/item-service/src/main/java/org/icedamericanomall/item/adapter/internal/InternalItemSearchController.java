package org.icedamericanomall.item.adapter.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.dto.SpuSearchPageCO;
import org.icedamericanomall.dto.SpuSearchQueryCO;
import org.icedamericanomall.common.utils.IdGenerator;
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
     * <p><b>入参口径（ADR-062 §决策 1/4、契约 §1.9③）</b>：{@code {spuId}} 为
     * <b>SPU 业务键 32-hex 单形态</b>（{@code spu.spu_id}）—— 原「纯数字 = 技术键 /
     * 32-hex = 业务键」的<b>双形态入参已废止</b>（它是绕开「不新增端点」门禁的垫片，
     * 代价是永久留下一个接受技术键的入口）；形态非法 → 400 {@code PARAM_ERROR} fail-fast，
     * 不再「静默落 null」。消费方：search-service 的 publishTime 回填（业务键）与
     * 商品向量摄取/相似检索的锚点解析（业务键）。
     */
    @GetMapping("/detail/{spuId}")
    public SpuSearchItemCO detail(@PathVariable("spuId") String spuId) {
        return itemSearchQueryManager.detail(IdGenerator.requireWire("spuId", spuId));
    }
}
