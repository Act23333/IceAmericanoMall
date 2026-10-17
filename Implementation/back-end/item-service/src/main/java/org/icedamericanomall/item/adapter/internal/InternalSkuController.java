package org.icedamericanomall.item.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.request.StockOpReq;
import org.icedamericanomall.item.app.SpuManager;
import org.icedamericanomall.item.app.SkuManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/internal/item/sku")
@RequiredArgsConstructor
public class InternalSkuController {

    private final SkuManager skuManager;
    private final SpuManager spuManager;

    /**
     * 批量查 SKU（线格式查询参数 {@code sku_ids}，逗号分隔的 <b>SKU 业务键</b> 32-hex；
     * Feign 侧 {@code SkuClient} 同口径）。
     *
     * <p>口径依据：契约 §1.8②（名称含 {@code sku_id}/{@code sku_ids} 的查询参数按业务键消费，
     * 服务端负责解析为技术键）+ ADR-061 §决策 2。解析走 {@code sku.sku_id} 唯一索引，
     * 与按技术键性能等价；A 案下 {@code cart.sku_id} 等内部外键保持技术键不动。
     */
    @GetMapping("/list/batch")
    public List<SkuCO> getSkuListByIds(@RequestParam(name = "sku_ids") List<String> skuIds) {
        return skuManager.getCosByIds(skuIds);
    }

    /**
     * Batch deduct stock (for order creation).
     * Internal endpoint — returns raw void, no Result wrapping.
     *
     * <p>{@code skuId} 口径为 <b>SKU 业务键</b> 32-hex（ADR-061 §决策 2）；
     * <b>形态非法</b>由本层 {@link IdGenerator#requireWire} fail-fast →
     * {@code PARAM_ERROR(1101)} / <b>HTTP 400</b>（ADR-062 §决策 7，批次 ③ 拆双义错误码）；
     * <b>形态合法但 SKU 不存在</b> → {@code SKU_NOT_FOUND}（fail-closed，禁止静默放过）。
     * 两者<b>不得</b>合流：拼错一个 hex 字符是调用方的参数错误，不是「商品已下架」。
     */
    @PostMapping("/deduct")
    public void deductStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuManager.deductStock(IdGenerator.requireWire("sku_id", item.getSkuId()),
                    item.getQuantity());
        }
    }

    /**
     * Batch restore stock (for order cancellation / timeout).
     * Internal endpoint — returns raw void, no Result wrapping.
     *
     * <p>{@code skuId} 口径同 {@code /deduct}；解析不到按「SKU 已不存在」幂等跳过
     * （取消/超时补偿链路不得因 SKU 已删而失败）。本端点<b>刻意不做形态 fail-fast</b>：
     * 补偿链路即使上游传来脏键也必须走完（否则一次回滚会把整批订单卡在异常态），
     * 脏键记 WARN 后按「不存在」跳过。
     */
    @PostMapping("/restore")
    public void restoreStock(@RequestBody @Valid List<StockOpReq> items) {
        for (StockOpReq item : items) {
            skuManager.restoreStock(item.getSkuId(), item.getQuantity());
        }
    }
}
