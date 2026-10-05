package org.icedamericanomall.marketing.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.marketing.app.promotion.PromotionQueryManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 促销活动内部端点（I44 {@code POST /internal/promotion/query}，F5 促销引擎，V5.8）
 * —— 仅集群内 Feign 调用（trade-service 价格计算中心，ia-api {@code PromotionClient}）。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}，业务异常以原始 HTTP 状态 + 极简
 * {@code {"code","msg"}} 透出（ADR-004）。
 *
 * <p><b>降级口径（ADR-043）</b>：返回值被调用方解释为业务事实「该店铺有/没有生效促销」，
 * 静默空集合 = 故障伪装成无促销；故调用方降级为 fail-closed（{@code PromotionClientFallback}
 * 抛 {@code SYSTEM_BUSY}）。本端点「无生效活动 → 空集合」是合法业务态，与查询失败严格区分。
 */
@RestController
@RequestMapping("/internal/promotion")
@RequiredArgsConstructor
public class InternalPromotionController {

    private final PromotionQueryManager promotionQueryManager;

    /**
     * 查询商家当前生效（进行中）的促销活动集合。
     *
     * @param query {@code PromotionQueryCO { sellerId 必填 }}
     * @return 进行中活动列表（按 id 升序）；无生效活动时为空集合
     */
    @PostMapping("/query")
    public List<PromotionCO> query(@RequestBody @Valid PromotionQueryCO query) {
        return promotionQueryManager.queryActive(query);
    }
}
