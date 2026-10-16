package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;

/**
 * 商品详情查询端口（领域层定义，基础设施层用 Feign 实现）。
 *
 * <p><b>标识口径（ADR-062 §决策 3）</b>：跨出服务边界一律 SPU <b>业务键</b>（32-hex），
 * 技术键只在 item 域的服务内/同库范围内有效。
 */
public interface SpuDetailPort {

    /**
     * 按 SPU 业务键查询详情。
     *
     * @param spuId SPU 业务键（32-hex，{@code spu.spu_id}；来源为各列表/搜索响应的 {@code spu_id}）
     * @return 商品详情；商品不存在返回 null
     * @throws org.icedamericanomall.common.exception.BizException 下游不可用（fail-closed 降级）
     */
    SpuDetailInfo getSpuDetail(String spuId);
}
