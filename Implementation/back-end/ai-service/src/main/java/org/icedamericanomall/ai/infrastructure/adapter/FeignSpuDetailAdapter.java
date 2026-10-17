package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.icedamericanomall.client.SpuDetailClient;
import org.icedamericanomall.dto.SpuDetailCO;
import org.springframework.stereotype.Component;

/**
 * {@link SpuDetailPort} 的 Feign 实现（防腐层）—— 把 item-service 的 CO 转成本上下文领域对象。
 *
 * <p><b>承载层（2026-10-18「甲b」，契约 §4.14.5.2 I70）</b>：{@link SpuDetailClient} 已迁到
 * 内部面 {@code /internal/item/spu}，<b>不再包 {@code Result} 信封</b> —— 故此处直接消费裸
 * {@link SpuDetailCO}。这层改动是「承载层搬家」的必然结果，不改变本适配器的职责：
 * 外部 CO → 本上下文领域对象 {@link SpuDetailInfo}（防腐层不把外部模型漏进领域层）。
 *
 * <p><b>{@code spuName}（契约 §1.8④）</b>：CO 侧字段名与线上键 {@code spu_name} 同批改定；
 * 本上下文的 {@link SpuDetailInfo#name()} 是<b>领域命名空间</b>（只经 {@code SpuDetailTool}
 * 渲染成文本进 LLM 上下文，<b>不出任何报文</b>），按契约 §1.8④ 的「不改清单」保持 {@code name}。
 */
@Component
@RequiredArgsConstructor
public class FeignSpuDetailAdapter implements SpuDetailPort {

    private final SpuDetailClient spuDetailClient;

    @Override
    public SpuDetailInfo getSpuDetail(String spuId) {
        SpuDetailCO detail = spuDetailClient.getSpuDetail(spuId);
        if (detail == null) {
            return null;
        }
        return new SpuDetailInfo(detail.spuId(), detail.spuName(), detail.brand(),
                detail.description(), detail.soldCount(), detail.attributes());
    }
}
