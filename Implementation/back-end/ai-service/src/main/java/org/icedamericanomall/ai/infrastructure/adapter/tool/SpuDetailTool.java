package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.exception.UpstreamException;
import org.springframework.stereotype.Component;

/**
 * V3.0.2: @Tool — 商品详情查询，供 Search/Compare Subagent 使用。
 * <p>
 * 仅依赖领域端口 {@link SpuDetailPort}，Feign 调用在基础设施层（防腐层）。
 *
 * <p><b>承载层变更对本工具不可见（2026-10-18「甲b」，契约 §4.14.5.2 I70）</b>：
 * 数据来源已由公开 P2 迁到内部面 {@code GET /internal/item/spu/{spuId}}（裸 CO、无
 * {@code Result} 信封），但本类只与端口打交道 —— 渲染文本用的 {@code name} 是领域对象
 * {@code SpuDetailInfo} 的字段（不进任何报文，§1.8④ 不改清单），故工具层零改动。
 *
 * <p><b>失败语义按 {@code category} 分流（2026-10-18 批次 ⑤，§三·八·1/3）</b>：
 * 本方法产出的字符串会原样进 LLM 上下文，因此它决定「用户最终听到什么」。此前一切异常都被
 * 折叠成「查询商品详情失败」，而下游 404 又被降级文案写成「商品服务暂不可用」——
 * 用户听到的永远是「服务故障」，真实业务事实（<b>商品不存在</b>）被彻底淹没。
 * 现在改为<b>读错误体自带类别</b>，不再凭状态码或文案猜测：
 * <ul>
 *   <li>{@link ErrorCategory#CLIENT}（下游 4xx：不存在 / 参数非法）→ 直说「商品不存在」等
 *       <b>业务事实</b>（不是故障，别让用户白等）；</li>
 *   <li>{@link ErrorCategory#TRANSIENT} / {@link ErrorCategory#BUG} / 非上游异常 →
 *       「<b>暂时无法获取</b>」（§三·八·2：该措辞不得写成「服务不可用」）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuDetailTool {

    /** 非 CLIENT 失败（5xx / 超时 / 熔断 / 未知）的统一措辞 —— 「暂时无法获取」，不是「服务不可用」。 */
    private static final String UNAVAILABLE_TEXT = "商品详情暂时无法获取，请稍后再试";

    private final SpuDetailPort spuDetailPort;

    /**
     * 查询商品详情。
     *
     * @param spuId SPU <b>业务键</b>（32-hex 字符串，来源为搜索/列表响应的 {@code spu_id}；
     *              ADR-062 §决策 3：跨服务边界不收技术键）
     * @return 商品详情文本；商品不存在返回业务事实文案，暂时性故障返回「暂时无法获取」
     */
    @Tool("获取指定商品的详细信息：名称、品牌、描述、属性参数、图片等。spuId 传搜索/列表结果中的 spu_id（32 位字符串）")
    public String getSpuDetail(String spuId) {
        try {
            SpuDetailInfo detail = spuDetailPort.getSpuDetail(spuId);
            if (detail == null) {
                return "未找到商品 ID=" + spuId;
            }
            return String.format("商品详情 — %s (ID:%s)\n品牌:%s\n描述:%s\n销量:%s\n属性:%s",
                    detail.name(), detail.spuId(), detail.brand(),
                    detail.description(), detail.soldCount(), detail.attributes());
        } catch (UpstreamException upstream) {
            return renderUpstreamFailure(spuId, upstream);
        } catch (Exception e) {
            log.warn("SpuDetailTool error: spuId={}, msg={}", spuId, e.getMessage());
            return UNAVAILABLE_TEXT;
        }
    }

    /**
     * 把上游失败渲染成 LLM 可用的文本——<b>按 {@code category} 分支</b>，不按状态码猜。
     *
     * @param spuId    查询的 SPU 业务键
     * @param upstream 上游失败（携带下游类别 / 业务码 / 文案）
     * @return 面向用户的文本
     */
    @SuppressWarnings("deprecation") // 兼容存量下游仍抛已退役的 PRODUCT_NOT_FOUND(3011/404)
    private String renderUpstreamFailure(String spuId, UpstreamException upstream) {
        if (upstream.getCategory() == ErrorCategory.CLIENT) {
            // 下游明确答复「业务上不存在 / 参数非法」：这是业务事实，不是故障
            boolean notFound = upstream.getCode() == ErrorCode.NO_RESOURCE_FOUND.getCode()
                    || upstream.getCode() == ErrorCode.PRODUCT_NOT_FOUND.getCode();
            log.info("SpuDetailTool 下游判定业务失败: spuId={}, code={}, msg={}",
                    spuId, upstream.getCode(), upstream.getMsg());
            return notFound
                    ? "商品不存在或已下架（spuId=" + spuId + "）"
                    : "商品参数无效，请给出正确的 spu_id（spuId=" + spuId + "）";
        }
        log.warn("SpuDetailTool 下游暂时不可用: spuId={}, category={}, code={}, traceId={}",
                spuId, upstream.getCategory(), upstream.getCode(), upstream.getTraceId());
        return UNAVAILABLE_TEXT;
    }
}
