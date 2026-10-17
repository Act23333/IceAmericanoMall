package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.LogisticsClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 物流记录创建降级工厂 —— <b>fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p><b>为什么改前（静默 void）是缺陷</b>：唯一调用方
 * {@code trade-service OrderLifecycleManager#shipOrder} 的写法是
 * <pre>
 * try { logisticsClient.createLogistics(dto); log.info("物流记录创建成功"); }
 * catch (Exception e) { log.error("物流记录创建失败，需人工处理"); }
 * </pre>
 * 旧实现静默返回，于是 logistics-service 不可用时日志<b>宣称「创建成功」</b>，
 * 而 {@code order_logistics} 里根本没有该包裹记录 —— 买家查不到物流、运维也无告警。
 * 抛错后由那段 {@code catch} 打出「需人工处理」的 ERROR（发货事实早已落库，主流程不受影响）。
 *
 * <p>分支：{@code category == CLIENT} → 原样抛 {@link UpstreamException}；
 * {@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}。
 */
@Slf4j
@Component
public class LogisticsClientFallback implements FallbackFactory<LogisticsClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法写入」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "物流记录暂时无法写入，请稍后重试";

    @Override
    public LogisticsClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new LogisticsClient() {

            @Override
            public void createLogistics(CreateLogisticsCO dto) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("物流记录创建降级(透传): 下游业务失败 orderId={}, packageNo={}, code={}, msg={}",
                            dto == null ? null : dto.getOrderId(),
                            dto == null ? null : dto.getPackageNo(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("物流记录创建降级: 暂时无法写入（category={}；禁止静默成功——调用方会打出"
                        + "「创建成功」而 order_logistics 无记录） orderId={}, packageNo={}", category,
                        dto == null ? null : dto.getOrderId(),
                        dto == null ? null : dto.getPackageNo(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
