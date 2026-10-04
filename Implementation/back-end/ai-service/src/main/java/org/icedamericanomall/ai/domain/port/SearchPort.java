package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.product.ProductHit;

import java.util.List;

/**
 * 商品关键词搜索端口（领域层定义，基础设施层用 Feign 实现）。
 *
 * <p>失败语义：搜索是对话的增强能力，下游不可用时抛
 * {@code BizException(AI_SERVICE_UNAVAILABLE)}，由调用方（Agent 工具）兜底为友好文案。
 */
public interface SearchPort {

    /**
     * 关键词搜索商品。
     *
     * @param keyword 搜索关键词
     * @param size    返回条数
     * @return 命中商品列表（无结果为空列表，不返回 null）
     * @throws org.icedamericanomall.common.exception.BizException 搜索服务不可用（fail-soft 降级）
     */
    List<ProductHit> searchProducts(String keyword, int size);
}
