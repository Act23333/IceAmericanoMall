package org.icedamericanomall.item.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.icedamericanomall.item.app.FreightTemplateManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运费模板内部端点（I43 {@code POST /internal/item/freight-template/query}，F2 运费模型）
 * —— 仅集群内 Feign 调用（trade-service 下单计价）。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}，业务异常以原始 HTTP 状态 + 极简
 * {@code {"code","msg"}} 透出（ADR-004）。
 *
 * <p><b>返回口径</b>：仅该商家<b>启用</b>模板；并<b>兜底补入</b>商家默认模板
 * （最早创建的启用模板，{@code isDefault=true}），供调用方完成「未绑定/绑定已停用 → 回落默认」
 * 的解析（见 04-Data-Model §十二 12.2 计价规则）。
 */
@RestController
@RequestMapping("/internal/item/freight-template")
@RequiredArgsConstructor
public class InternalFreightTemplateController {

    private final FreightTemplateManager freightTemplateManager;

    /**
     * 批量查询商家启用运费模板。
     *
     * @param query {@code { sellerId 必填, templateIds 可空数组 }}
     * @return 启用模板列表（含 isDefault 标记）
     */
    @PostMapping("/query")
    public List<FreightTemplateCO> query(@RequestBody FreightTemplateQueryCO query) {
        return freightTemplateManager.queryEnabled(query);
    }
}
