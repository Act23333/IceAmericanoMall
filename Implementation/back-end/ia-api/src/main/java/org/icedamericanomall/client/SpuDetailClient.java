package org.icedamericanomall.client;

import org.icedamericanomall.config.DefaultFeignConfig;
import org.icedamericanomall.dto.SpuDetailCO;
import org.icedamericanomall.fallback.SpuDetailClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for item-service spu detail（调用方 {@code ai-service} 的 Search/Compare Subagent）。
 *
 * <p><b>承载层（2026-10-18「甲b」，契约 §4.14.5.2 I70）</b>：走<b>内部面</b>
 * {@code /internal/item/spu}（不过网关、<b>不包 {@code Result} 信封</b>）——
 * 此前 {@code path = "/api/item/spu"} 属「服务间调用却走公开契约」的既有偏差
 * （ADR-062 §后果 E7 登记的待根治项），本次随 I70 落码一并迁正。
 * 内部面的错误语义按 ADR-004：业务异常返回<b>真 HTTP 状态码</b> + 极简
 * {@code {"code","msg"}}（不是 200 信封），故返回类型为裸 {@code SpuDetailCO}；
 * 商品不存在 → 404 {@code NO_RESOURCE_FOUND(1103)}；业务键形态非法 → 400 {@code PARAM_ERROR(1101)}。
 *
 * <p><b>与 item 侧 {@code SpuVO} 必须同批改名</b>：{@code SpuDetailCO.spuName}（线上键
 * {@code spu_name}，契约 §1.8④）与 {@code SpuVO.spuName} 是同一条报文的发送端与接收端 ——
 * Jackson {@code SNAKE_CASE} 会<b>忽略未知键</b>，只改一侧时另一侧<b>静默落 {@code null}</b>
 * （不报错、只是 AI 工具拿不到商品名），故两者必须在同一 PR 内改完。
 *
 * <p><b>降级语义：fail-closed</b>（抛 {@code BizException}）。虽然商品详情是展示型数据，
 * 但调用方 {@code SpuDetailTool} 会把「取不到」解释为「商品不存在」，降级返回 null 会污染业务语义。
 * 详见 {@link SpuDetailClientFallback}。
 *
 * <p><b>寻址口径（ADR-062 §决策 2/3）</b>：路径变量是 <b>SPU 业务键 32-hex {@code String spuId}</b>
 * —— 服务间调用同样不得传技术键（它只在服务内、同库范围内有含义）。
 * 形态非法时 item 侧返回 400，降级工厂按 fail-closed 语义处理。
 */
@FeignClient(
    name = "item-service",
    path = "/internal/item/spu",
    contextId = "spu",
    configuration = DefaultFeignConfig.class,
    fallbackFactory = SpuDetailClientFallback.class
)
public interface SpuDetailClient {

    /**
     * 获取商品详情（内部面，裸 CO、无 {@code Result} 信封）。
     *
     * @param spuId SPU 业务键（32-hex，{@code spu.spu_id}）
     * @return 商品详情；商品不存在时下游返回 404（Feign 抛错 → 降级工厂按 fail-closed 处理）
     */
    @GetMapping("/{spuId}")
    SpuDetailCO getSpuDetail(@PathVariable String spuId);
}
