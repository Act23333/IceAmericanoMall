package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SpuDetailClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuDetailCO;
import org.icedamericanomall.exception.UpstreamException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品详情服务降级工厂 —— <b>按 {@code 09-constraints} §三·八 分两路：4xx 透传，5xx/超时/熔断降级</b>。
 *
 * <p><b>本类修掉的真实缺陷（2026-10-18 批次 ⑤）</b>：此前本工厂对<b>任何</b> {@code cause}
 * 一律 {@code throw BizException(SYSTEM_BUSY, "商品服务暂不可用")} —— 于是下游
 * <b>404「商品不存在」</b>（{@code NO_RESOURCE_FOUND(1103)}，产品语义上是一条<b>业务事实</b>）
 * 被翻译成「服务故障」。唯一调用方 {@code ai-service SpuDetailTool} 把这句故障文案
 * 原样喂给 LLM，用户最终看到「商品服务暂不可用」而不是「商品不存在」——
 * 这正是 §三·八 立条时点名的反例。
 *
 * <p><b>两路分支（不要合并）</b>：
 * <ul>
 *   <li><b>{@code category == CLIENT}</b>（4xx：参数 / 资源不存在 / 凭证 / 越权 / 冲突）：
 *       <b>原样抛出 {@link UpstreamException}</b> —— 业务码（如 1103）与文案一字不改，
 *       让上层按业务语义呈现。此处<b>不</b>改写成本域错误码，也不吞成 {@code null}
 *       （吞成 {@code null} 会让工具把「服务抖动」说成「商品不存在」，是同一枚硬币的另一面）。</li>
 *   <li><b>其余</b>（{@code TRANSIENT} 5xx/429/超时/熔断、{@code BUG}、以及拿不到
 *       {@code UpstreamException} 的连接层失败）：仍然 <b>fail-closed</b> 抛
 *       {@code BizException(SYSTEM_BUSY)} —— 展示型数据也不许伪装成「成功但没数据」。
 *       文案按 §三·八·2 改为「<b>暂时无法获取</b>」：此前写「商品服务暂不可用」，违反
 *       「降级文案不得写成『服务不可用』」那一条（用户读到的应是「这会儿取不到」，而不是
 *       「这服务坏了」——后者会让人怀疑整个平台）。</li>
 * </ul>
 *
 * <p><b>为什么仍是 fail-closed（不是 ① 类展示型查询）</b>：本包的判定标准是「返回空会不会被
 * 调用方解释成一条业务事实」。这里答案是<b>会</b>（{@code SpuDetailTool} 把 {@code null}
 * 渲染成「未找到商品」），故属 ② 判别型 —— 见本包 {@code package-info}。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}（ai-service 已满足），详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SpuDetailClientFallback implements FallbackFactory<SpuDetailClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」，<b>不得</b>写成「服务不可用」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "商品详情暂时无法获取，请稍后重试";

    @Override
    public SpuDetailClient create(Throwable cause) {
        return new SpuDetailClient() {

            @Override
            public SpuDetailCO getSpuDetail(String spuId) {
                UpstreamException upstream = UpstreamException.findIn(cause);
                if (upstream != null && upstream.isClientError()) {
                    // ★ 透传：下游说「不存在/参数错」，这就是业务事实，原样往上抛（含原始 code/文案）
                    log.warn("商品详情服务降级(透传): 下游业务失败 spuId={}, upstreamStatus={}, code={}, category={}, msg={}",
                            spuId, upstream.getUpstreamStatus(), upstream.getCode(),
                            upstream.getCategory(), upstream.getMsg());
                    throw upstream;
                }
                log.error("商品详情服务降级: 暂时无法获取（5xx/超时/熔断；禁止降级为 null，"
                        + "AI 工具会误判为「商品不存在」）spuId={}, cause={}",
                        spuId, cause == null ? "unknown" : cause.toString(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
