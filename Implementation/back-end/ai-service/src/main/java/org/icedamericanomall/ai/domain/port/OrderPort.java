package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.order.OrderBrief;

/**
 * 订单查询端口（领域层定义，基础设施层用 Feign 实现）。
 */
public interface OrderPort {

    /**
     * 按订单号查询订单摘要。
     *
     * @param orderNo 订单号
     * @return 订单摘要；订单不存在返回 null
     * @throws org.icedamericanomall.common.exception.BizException 下游不可用（fail-closed 降级）
     */
    OrderBrief getOrder(String orderNo);
}
