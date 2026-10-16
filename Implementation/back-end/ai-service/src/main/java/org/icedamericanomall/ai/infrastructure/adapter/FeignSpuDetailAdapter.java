package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.icedamericanomall.client.SpuDetailClient;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.dto.SpuDetailCO;
import org.springframework.stereotype.Component;

/**
 * {@link SpuDetailPort} 的 Feign 实现（防腐层）——把 item-service 的 CO 转成本上下文领域对象。
 */
@Component
@RequiredArgsConstructor
public class FeignSpuDetailAdapter implements SpuDetailPort {

    private final SpuDetailClient spuDetailClient;

    @Override
    public SpuDetailInfo getSpuDetail(Long spuId) {
        Result<SpuDetailCO> result = spuDetailClient.getSpuDetail(spuId);
        if (result == null || result.getData() == null) {
            return null;
        }
        SpuDetailCO detail = result.getData();
        return new SpuDetailInfo(detail.spuId(), detail.name(), detail.brand(),
                detail.description(), detail.soldCount(), detail.attributes());
    }
}
