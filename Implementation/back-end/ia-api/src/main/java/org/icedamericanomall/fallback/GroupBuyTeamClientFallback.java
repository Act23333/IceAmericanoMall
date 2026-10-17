package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyTeamClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 结团退款联动降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p><b>为什么改前（fail-open）是缺陷</b>：{@code refundTeam} 是 <b>void 结果对象</b>，
 * 调用方 {@code marketing-service GroupBuyParticipationManager#triggerTeamRefund} 在正常返回后
 * 立刻打「[置已失败并触发退款] teamNo=… orderCount=…」——旧实现静默返回，
 * 于是 trade-service 不可用时日志<b>宣称已触发退款</b>，而团内订单实际既未取消也未退款。
 * 这正是判据表第 1 行「void 静默成功 = 操作丢了」。
 *
 * <p><b>抛错不会扩大故障面（已核实）</b>：唯一调用点
 * {@code expireOverdueTeams()} 在逐团的 {@code try/catch} 内调用它（单团失败继续下一团，
 * 记 {@code WARN}），因此异常<b>只把「谎报的触发成功」变成「如实告警」</b>，
 * 结团扫描与其余团的处理一字不变。
 *
 * <p>分支：{@code category == CLIENT} → 原样抛 {@link UpstreamException}；
 * {@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}。
 */
@Slf4j
@Component
public class GroupBuyTeamClientFallback implements FallbackFactory<GroupBuyTeamClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法处理」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "结团退款暂时无法触发，请稍后重试";

    @Override
    public GroupBuyTeamClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new GroupBuyTeamClient() {

            @Override
            public void refundTeam(GroupTeamRefundCO refund) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("结团退款联动降级(透传): 下游业务失败 teamNo={}, orderNos={}, code={}, msg={}",
                            refund == null ? null : refund.getTeamNo(),
                            refund == null ? null : refund.getOrderNos(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("结团退款联动降级: 暂时无法触发（category={}；禁止静默成功——调用方会打出"
                                + "「已触发退款」的日志而团内订单实际未退） teamNo={}, orderNos={}", category,
                        refund == null ? null : refund.getTeamNo(),
                        refund == null ? null : refund.getOrderNos(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
