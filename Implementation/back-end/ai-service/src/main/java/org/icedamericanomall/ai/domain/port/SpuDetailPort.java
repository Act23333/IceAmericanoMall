package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;

/**
 * 商品详情查询端口（领域层定义，基础设施层用 Feign 实现）。
 */
public interface SpuDetailPort {

    /**
     * 按商品ID查询详情。
     *
     * @param spuId 商品ID
     * @return 商品详情；商品不存在返回 null
     * @throws org.icedamericanomall.common.exception.BizException 下游不可用（fail-closed 降级）
     */
    SpuDetailInfo getSpuDetail(Long spuId);
}
