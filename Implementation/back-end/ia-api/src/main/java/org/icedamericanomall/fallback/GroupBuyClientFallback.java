package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 拼团参团联动降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>，属 ADR-011 的
 * <b>② 判别型查询 + 拼团价前提命令</b>。
 *
 * <p>依据（读调用方得出）：{@code validate} 的回执里带 {@code groupPrice}（拼团价是计价业务事实），
 * {@code join} 是参团状态写。两者降级返回任意结果都会污染拼团价口径或让拼团单停留在团外
 * （以拼团价买到非拼团商品）。调用方 {@code trade-service} 已按「下单失败可重试；
 * 参团失败 → 取消 + 恢复库存 + I55 退款自动闭环」处理异常（ADR-049），
 * 故抛错不会造成悬挂，反而是闭环的触发器。
 */
@Slf4j
@Component
public class GroupBuyClientFallback implements FallbackFactory<GroupBuyClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "拼团信息暂时无法获取，请稍后重试";

    @Override
    public GroupBuyClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new GroupBuyClient() {

            @Override
            public GroupBuyValidateResultCO validate(GroupBuyValidateCO query) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("拼团下单校验降级(透传): 下游业务失败 activityId={}, teamNo={}, code={}, msg={}",
                            query == null ? null : query.getActivityId(),
                            query == null ? null : query.getTeamNo(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("拼团下单校验降级: 暂时无法获取（category={}；拼团价口径不得静默污染）"
                                + " activityId={}, teamNo={}", category,
                        query == null ? null : query.getActivityId(),
                        query == null ? null : query.getTeamNo(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }

            @Override
            public GroupTeamCO join(GroupJoinCO join) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("拼团参团降级(透传): 下游业务失败（团满/已结/重复等业务事实）orderNo={}, teamNo={}, code={}, msg={}",
                            join == null ? null : join.getOrderNo(),
                            join == null ? null : join.getTeamNo(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("拼团参团降级: 暂时无法处理（category={}；不得静默假装参团成功）"
                                + " orderNo={}, teamNo={}", category,
                        join == null ? null : join.getOrderNo(),
                        join == null ? null : join.getTeamNo(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
