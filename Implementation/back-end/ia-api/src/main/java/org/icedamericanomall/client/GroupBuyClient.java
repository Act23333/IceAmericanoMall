package org.icedamericanomall.client;

import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.fallback.GroupBuyClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 拼团参团联动 Feign 客户端（F13 拼团：trade-service → marketing-service，契约 §4.14.8c I52/I53，V5.14）。
 *
 * <p><b>降级语义：fail-closed（抛 {@code BizException}）</b>，属 ADR-011 三分法的 <b>② 判别型查询/资金前提命令</b>：
 * <ul>
 *   <li>I52 返回的拼团价是计价业务事实——静默降级等于「故障伪装成无拼团价」，价格口径被污染；</li>
 *   <li>I53 参团失败（团满/已结/重复）必须以异常透出——trade 捕获后走「取消 + 恢复库存 + I55 退款」
 *       自动闭环（ADR-049），绝不允许静默假装参团成功（拼团单停留在团外 = 用户以拼团价买到非拼团商品）。</li>
 * </ul>
 */
@FeignClient(name = "marketing-service", path = "/internal/group-buy",
        contextId = "groupBuy", fallbackFactory = GroupBuyClientFallback.class)
public interface GroupBuyClient {

    /** 下单前置校验（活动进行中 + 参团时团有效未满 + 用户未在该团），返回拼团价等建单依据。 */
    @PostMapping("/validate")
    GroupBuyValidateResultCO validate(@RequestBody GroupBuyValidateCO query);

    /** 开团（teamNo 空）/参团（非空）；参团竞态由条件更新兜底，满员返回 status=2。 */
    @PostMapping("/join")
    GroupTeamCO join(@RequestBody GroupJoinCO join);
}
