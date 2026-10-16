package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.springframework.stereotype.Component;

/**
 * V3.0.2: @Tool — 商品详情查询，供 Search/Compare Subagent 使用。
 * <p>
 * 仅依赖领域端口 {@link SpuDetailPort}，Feign 调用在基础设施层（防腐层）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuDetailTool {

    private final SpuDetailPort spuDetailPort;

    /**
     * 查询商品详情。
     *
     * @param spuId 商品ID
     * @return 商品详情文本；商品不存在或查询失败返回提示文案
     */
    @Tool("获取指定商品的详细信息：名称、品牌、描述、属性参数、图片等")
    public String getSpuDetail(Long spuId) {
        try {
            SpuDetailInfo detail = spuDetailPort.getSpuDetail(spuId);
            if (detail == null) {
                return "未找到商品 ID=" + spuId;
            }
            return String.format("商品详情 — %s (ID:%s)\n品牌:%s\n描述:%s\n销量:%s\n属性:%s",
                    detail.name(), detail.spuId(), detail.brand(),
                    detail.description(), detail.soldCount(), detail.attributes());
        } catch (Exception e) {
            log.warn("SpuDetailTool error: {}", e.getMessage());
            return "查询商品详情失败，请稍后重试";
        }
    }
}
