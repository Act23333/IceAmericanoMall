package org.icedamericanomall.item.adapter.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.dto.SpuDetailCO;
import org.icedamericanomall.item.app.SpuManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品详情内部端点 —— I70 {@code GET /internal/item/spu/{spuId}}（契约 §4.14.5.2，2026-10-18 用户裁决）。
 *
 * <p><b>为什么需要它</b>：{@code ia-api} 的 {@code SpuDetailClient}（调用方 {@code ai-service}
 * {@code FeignSpuDetailAdapter}，活链路）此前是<b>服务间调用走公开契约</b>
 * —— {@code @FeignClient(path = "/api/item/spu")} 打 {@code GET /api/item/spu/{spuId}}
 * （ADR-062 §后果 E7 登记的待根治项）。按契约 §1.9② 与 {@code 09-constraints} §三·六，
 * 服务间调用应走 {@code /internal/**}：不过网关、不包 {@code Result} 信封。
 * 本端点是那条偏差的补正路，<b>不是新口径</b>。
 *
 * <p><b>与公开 P2 的关系（硬约束）</b>：这是<b>同一份详情数据的内部出口</b>，
 * 不另建装配逻辑 —— {@link SpuManager#getSpuDetailCO(String)} 直调
 * {@link SpuManager#getSpuDetail(String)}（P2 唯一装配源），只做纯投影;
 * 两套装配一定会漂移，而契约要求二者逐字段同源。
 *
 * <p><b>承载层纪律（ADR-004）</b>：本方法返回<b>裸 {@code SpuDetailCO}</b>，<b>不包
 * {@code Result}</b>；失败时由 {@code GlobalExceptionHandler} 透出<b>真 HTTP 状态码 +
 * 类型化错误体 {@code ErrorInfo}</b>（{@code code / msg / category / retryable / traceId}，
 * 2026-10-18 批次 ⑤ 起；此前只有 {@code code} + {@code msg}）——
 * 业务键形态非法 → 400 {@code PARAM_ERROR(1101)}；
 * 商品不存在/已删除 → 404 {@code NO_RESOURCE_FOUND(1103)}，{@code category=CLIENT}，
 * 调用方据此<b>原样透传</b>「商品不存在」，不得改写为 {@code SYSTEM_BUSY}
 * （{@code collaboration/09-constraints.md} §三·八·1）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/item/spu")
@RequiredArgsConstructor
public class InternalItemSpuController {

    private final SpuManager spuManager;

    /**
     * 按 SPU 业务键取商品详情（内部契约，供 ai-service 等集群内调用方）。
     *
     * @param spuId SPU <b>业务键</b>（32-hex 单形态，{@code spu.spu_id}）；形态校验在适配层入口
     *              （{@link IdGenerator#requireWire}，非法即 400 fail-fast，绝不静默变成「查不到」）
     * @return 商品详情（裸 CO，不包信封）
     */
    @GetMapping("/{spuId}")
    public SpuDetailCO detail(@PathVariable("spuId") String spuId) {
        return spuManager.getSpuDetailCO(IdGenerator.requireWire("spuId", spuId));
    }
}
