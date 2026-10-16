package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.dto.SpuDetailCO;
import org.icedamericanomall.fallback.SpuDetailClientFallback;
import org.icedamericanomall.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * V3.0: Feign client for item-service spu detail.
 * Used by ai-service Search/Compare Subagents.
 *
 * <p>降级语义：<b>fail-closed</b>（抛 {@code BizException}）。虽然商品详情是展示型数据，
 * 但调用方 {@code SpuDetailTool} 把 {@code data == null} 判定为「商品不存在」，
 * 降级返回 null 会污染业务语义。详见 {@link SpuDetailClientFallback}。
 *
 * <p><b>已知偏差（待迁移，V5.3 审计记录；ADR-062 §后果 E7 登记）</b>：本客户端走的是
 * item-service 的<b>公开契约</b> {@code path = "/api/item/spu"}，而集群内服务间调用应统一走
 * {@code /internal/**}（见 ADR-004）。迁移需要 item-service 暴露对应内部端点，
 * 而「新增端点 → 端点对账 {@code codeOnly} +1」按 ADR-062 §决策 4 属<b>显式决策</b>，
 * 须单独立项（本批只做最小改动：路径段改名 + 路径变量收业务键，见下）；
 * 迁移完成前仍按公开契约解析 {@code Result} 信封。
 *
 * <p><b>寻址口径（ADR-062 §决策 2/3）</b>：路径段随 item 域由 {@code product} 改
 * {@code spu}；路径变量由技术键 {@code Long id} 改为 <b>SPU 业务键 32-hex {@code String spuId}</b>
 * —— 服务间调用同样不得传技术键（它只在服务内、同库范围内有含义）。
 * 形态非法时 item 侧返回 400，降级工厂按 fail-closed 语义处理。
 */
@FeignClient(
    name = "item-service",
    path = "/api/item/spu",
    contextId = "spu",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = SpuDetailClientFallback.class
)
public interface SpuDetailClient {

    /**
     * 获取商品详情。
     *
     * @param spuId SPU 业务键（32-hex，{@code spu.spu_id}）
     */
    @GetMapping("/{spuId}")
    Result<SpuDetailCO> getSpuDetail(@PathVariable String spuId);
}
