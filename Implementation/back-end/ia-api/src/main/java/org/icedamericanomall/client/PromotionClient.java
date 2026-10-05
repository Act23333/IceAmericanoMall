package org.icedamericanomall.client;

import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.fallback.PromotionClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 生效促销查询 Feign 客户端（F5 促销引擎：trade-service → marketing-service，契约 §4.14.8b I44，V5.8）。
 *
 * <p>调用方：trade-service 价格计算中心（ADR-043）——按 sellerId 取该店铺「进行中」促销集合，
 * 由定价中心按「减免金额最大者」规则计价并快照 {@code orders.discount_amount}。
 *
 * <p><b>降级语义：fail-closed（抛 {@code BizException}）</b>，属 ADR-011 三分法的
 * <b>② 判别型查询</b>：返回值会被解释为业务事实「该店铺有/没有生效促销」。若 marketing 抖动时
 * 静默降级为空集合，等于「系统故障伪装成无促销」——用户应得优惠被吞、且与买家侧促销展示
 * （管理端配置口径）产生价格不一致。故必须抛异常使下单失败（可重试），绝不静默按无促销计价。
 */
@FeignClient(name = "marketing-service", path = "/internal/promotion",
        contextId = "promotion", fallbackFactory = PromotionClientFallback.class)
public interface PromotionClient {

    /**
     * 查询商家当前生效（进行中）的促销活动集合。
     *
     * @param query 查询条件（sellerId 必填）
     * @return 生效中促销列表；无生效活动时为空集合（合法业务态）
     */
    @PostMapping("/query")
    List<PromotionCO> queryActive(@RequestBody PromotionQueryCO query);
}
