package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.GroupBuyValidateCO;
import org.icedamericanomall.dto.GroupBuyValidateResultCO;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 拼团参团联动降级工厂 —— <b>fail-closed（抛异常）</b>，属 ADR-011 的 <b>② 判别型查询/资金前提命令</b>。
 *
 * <p>I52 降级返回任意结果都会污染拼团价口径；I53 降级假装成功会让拼团单停留在团外
 * （以拼团价买到非拼团商品）。两者都必须抛异常：下单失败可重试；参团失败由 trade
 * 走「取消 + 恢复库存 + I55 退款」自动闭环（ADR-049）。
 */
@Slf4j
@Component
public class GroupBuyClientFallback implements FallbackFactory<GroupBuyClient> {

    @Override
    public GroupBuyClient create(Throwable cause) {
        return new GroupBuyClient() {

            @Override
            public GroupBuyValidateResultCO validate(GroupBuyValidateCO query) {
                log.error("拼团下单校验降级：marketing-service 不可用（拼团价口径不得静默污染）"
                                + " activityId={}, teamNo={}",
                        query == null ? null : query.getActivityId(),
                        query == null ? null : query.getTeamNo(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "拼团服务暂不可用，请稍后重试");
            }

            @Override
            public GroupTeamCO join(GroupJoinCO join) {
                log.error("拼团参团降级：marketing-service 不可用（不得静默假装参团成功）"
                                + " orderNo={}, teamNo={}",
                        join == null ? null : join.getOrderNo(),
                        join == null ? null : join.getTeamNo(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "拼团服务暂不可用，请稍后重试");
            }
        };
    }
}
