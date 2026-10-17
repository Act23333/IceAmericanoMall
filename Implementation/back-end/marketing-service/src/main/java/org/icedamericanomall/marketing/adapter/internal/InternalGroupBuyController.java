package org.icedamericanomall.marketing.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyParticipationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 拼团参团联动内部端点（I52/I53 {@code /internal/group-buy}，V5.14，ADR-049）
 * —— 仅集群内 Feign 调用（trade-service，ia-api {@code GroupBuyClient}）。
 *
 * <p>{@code /internal/**} 不包装 {@code Result}，业务异常以原始 HTTP 状态 + 类型化错误体
 * {@code ErrorInfo}（{@code code / msg / category / retryable / traceId}）透出（ADR-004）。
 *
 * <p><b>降级口径</b>：两者均 fail-closed（{@code GroupBuyClientFallback} 抛
 * {@code SYSTEM_BUSY}）——I52 拼团价是计价业务事实，I53 参团失败由 trade 走
 * 「取消 + 恢复库存 + I55 退款」自动闭环，绝不静默假装成功（ADR-049）。
 */
@RestController
@RequestMapping("/internal/group-buy")
@RequiredArgsConstructor
public class InternalGroupBuyController {

    private final GroupBuyParticipationManager groupBuyParticipationManager;

    /**
     * I52：拼团下单前置校验（活动进行中 + 参团时团有效未满 + 用户未在该团）。
     *
     * @param query {@code GroupBuyValidateCO}
     * @return 拼团价等建单依据（原始 CO，不包 Result）
     */
    @PostMapping("/validate")
    public GroupBuyValidateResultCO validate(@RequestBody @Valid GroupBuyValidateCO query) {
        return groupBuyParticipationManager.validate(query);
    }

    /**
     * I53：开团（teamNo 空）/参团（非空）；uk 双唯一键兜幂等，条件更新兜参团竞态。
     *
     * @param join {@code GroupJoinCO}
     * @return 团状态回执（满员时 status=2；原始 CO，不包 Result）
     */
    @PostMapping("/join")
    public GroupTeamCO join(@RequestBody @Valid GroupJoinCO join) {
        return groupBuyParticipationManager.join(join);
    }
}
