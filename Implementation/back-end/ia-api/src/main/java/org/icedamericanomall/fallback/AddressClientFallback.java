package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 收货地址查询降级工厂 —— <b>fail-closed（4xx 透传 / 其余降级为「暂时无法获取」）</b>。
 *
 * <p><b>为什么必须 fail-closed（读调用方代码得出，不照抄旧注释）</b>：唯一用途是
 * trade-service 四个下单策略的 {@code ctx.bindAddress(addressClient.getAddress(...))}：
 * <ul>
 *   <li>地址快照是<b>订单落库的必备输入</b>（{@code orders.receiver_*} 三列由其填充），
 *       拿不到地址就不能建单；</li>
 *   <li>{@code bindAddress} 同时是<b>越权防护唯一出口</b>（内部端点不校验归属），
 *       {@code address == null} 会抛 {@code PARAM_ERROR("收货地址不存在")}。</li>
 * </ul>
 * <p><b>改前缺陷</b>：降级返回 {@code null} ⇒ user-service 抖动被翻译成「收货地址不存在」
 * —— 用户被指责填错地址，运维侧却没有任何故障信号（{@code 09-constraints} §三·八·1 点名的
 * 「错误信息污染」）。本工厂因此不再返回 {@code null}。
 *
 * <p><b>两路分支</b>：
 * <ul>
 *   <li>{@code category == CLIENT}（下游 4xx，如地址不存在 1103）：<b>原样抛
 *       {@link UpstreamException}</b> —— 那是<b>业务事实</b>，保留原始 code/文案/traceId；</li>
 *   <li>{@code TRANSIENT} / {@code BUG}：抛 {@code BizException(SYSTEM_BUSY)}，文案按
 *       §三·八·2 为「暂时无法获取」（不得写成「服务不可用」）。</li>
 * </ul>
 */
@Slf4j
@Component
public class AddressClientFallback implements FallbackFactory<AddressClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」，<b>不得</b>写成「服务不可用」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "收货地址暂时无法获取，请稍后重试";

    @Override
    public AddressClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new AddressClient() {
            @Override
            public AddressCO getAddress(Long id) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("收货地址查询降级(透传): 下游业务失败 addressId={}, upstreamStatus={}, code={}, category={}, msg={}",
                            id, upstream.getUpstreamStatus(), upstream.getCode(), category, upstream.getMsg());
                    throw upstream;
                }
                log.error("收货地址查询降级: 暂时无法获取（category={}；禁止降级为 null——调用方会误判为"
                        + "「收货地址不存在」并掩盖 user-service 故障） addressId={}, cause={}",
                        category, id, cause == null ? "unknown" : cause.toString(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
