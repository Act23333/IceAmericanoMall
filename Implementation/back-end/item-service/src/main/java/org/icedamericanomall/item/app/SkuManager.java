package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.dto.SkuCO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SKU 编排（应用层）—— 原子扣减/恢复编排（不限量分支判定）与内部契约回填。
 *
 * <p><b>并发语义（ADR-020）</b>：库存校验与扣减在同一条 UPDATE 内完成，
 * 不限量的 SKU 仅累加销量；受影响行数为 0 即库存不足。
 *
 * <p><b>I13 商品展示字段（ADR-057）</b>：{@code sku} 表无商品名列，{@code SkuCO} 的
 * {@code spu_name/sellerId/categoryId/freightTemplateId} 由
 * {@link SkuSpuDisplayQuery} 联表 {@code spu} <b>整批一次</b>回填（无 N+1）；
 * 回填失败或商品缺失 → 字段留空（fail-open），SKU 本体照常返回。
 *
 * <p><b>业务键（ADR-058 §3 / ADR-061）</b>：同一次联表<B>顺带</B>带出
 * {@code sku.sku_id}/{@code spu.spu_id} 两个业务键，供 {@code SkuCO}/{@code SkuVO} 直接使用；
 * 调用方无需为拿业务键再打一次库。
 *
 * <p><b>「形态非法」不是「SKU 不存在」（批次 ③ 拆双义错误码，2026-10-18）</b>：
 * 本类此前把「业务键拼错」与「SKU 不存在」写成同一个 {@code SKU_NOT_FOUND(3016)}，
 * 现按 {@code collaboration/09-constraints.md} §三·六 拆开：
 * <table border="1">
 *   <caption>两种语义 → 两种承载</caption>
 *   <tr><th>语义</th><th>抛出</th><th>HTTP</th><th>判据</th></tr>
 *   <tr>
 *     <td><b>业务键形态非法</b>（拼错 / 长度错 / 非 hex）</td>
 *     <td>{@link ErrorCode#PARAM_ERROR}（1101）</td>
 *     <td><b>400</b></td>
 *     <td><b>参数/形态非法</b>（{@code CLIENT}）</td>
 *   </tr>
 *   <tr>
 *     <td><b>SKU 不存在</b>（形态合法但解析不到 / 行已删）</td>
 *     <td>{@link ErrorCode#SKU_NOT_FOUND}（3016）</td>
 *     <td>200（批次 ① 归位为 404 + 1103）</td>
 *     <td><b>资源不存在</b></td>
 *   </tr>
 * </table>
 * <p>形态校验的主责在适配层（{@code InternalSkuController#deductStock} 经
 * {@code IdGenerator#requireWire} fail-fast，ADR-062 §决策 7），本类保留同口径的第二道防线。
 * {@code 3016} 的 {@code HttpStatus} 本批<b>刻意不动</b>（先拆语义、再改状态码），归位由批次 ① 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkuManager {

    private final SkuRepository skuRepository;
    private final SkuConverter skuConverter;
    private final SkuSpuDisplayQuery skuSpuDisplayQuery;

    /**
     * 批量查 SKU 内部 CO（I13）—— 购物车回填与 trade 下单取数共用。
     *
     * <p><b>入参口径（ADR-061 §决策 2）</b>：{@code skuIds} 是 <b>SKU 业务键</b>
     * （32-hex {@code String}；Feign 侧 {@code SkuClient} 同口径）。同库内部解析为技术键
     * 只读一次（{@code sku.sku_id} 唯一索引，性能与按技术键等价——A 案：表结构不动），
     * 且该次查询<b>同时</b>带出 {@code SkuCO.skuId}/{@code spuId} 两个业务键与商品名
     * （{@link SkuSpuDisplayQuery} 单条联表，零额外往返）。
     *
     * <p>商品展示四字段（{@code spu_name}/{@code seller_id}/{@code category_id}/
     * {@code freight_template_id}）同样来自 {@code spu} 表，由 {@link #enrichSpuDisplay}
     * 整批回填；回填不成功时四字段为 null，SKU 本体照常返回（fail-open，ADR-057）。
     *
     * <p>业务键非法或不存在 → 该键不出现在结果中（不抛异常、不放大成 500）；调用方按
     * 「返回条数 != 请求条数」判定缺项（下单链路 fail-closed，见各 OrderCreateStrategy）。
     */
    public List<SkuCO> getCosByIds(List<String> skuIds) {
        Map<String, SkuSpuDisplay> displays = loadDisplaysByBusinessIds(skuIds);
        if (displays.isEmpty()) {
            return List.of();
        }
        List<Long> technicalIds = displays.values().stream()
                .map(SkuSpuDisplay::skuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<SkuCO> cos = skuConverter.toCOs(skuRepository.findByIds(technicalIds));
        enrichSpuDisplay(cos, byTechnicalId(displays.values()));
        return cos;
    }

    /** 商品在售 SKU 视图（商品详情）。 */
    public List<SkuVO> listOnSaleBySpu(Long spuId) {
        return skuConverter.toVOs(skuRepository.findOnSaleBySpuId(spuId));
    }

    /** 商品全部 SKU 视图（详情页含下架）。 */
    public List<SkuVO> listBySpu(Long spuId) {
        return skuConverter.toVOs(skuRepository.findBySpuId(spuId));
    }

    /**
     * 回填 SKU 视图的 <b>SPU 业务键</b>（{@code SkuVO.spuId}，ADR-061 §决策 2）。
     *
     * <p>{@code Sku} 聚合只持有 {@code spu.spu_id} 的<b>技术外键</b>，业务键须由
     * {@link SkuSpuDisplayQuery}（同库只读）带出 —— 与商品详情同一页，故<b>整批一次</b>查询
     * （无 N+1）。fail-open：查询异常 → 记 WARN 后字段留空，商品详情主流程照常返回（ADR-057）。
     *
     * @param spuBusinessKey 当前页所属 SPU 业务键（同一商品下恒同值；{@code null} 时跳过）
     * @param skuVos         待回填的 SKU 视图（原地修改）
     */
    public void fillSpuBusinessKey(String spuBusinessKey, List<SkuVO> skuVos) {
        if (spuBusinessKey == null || skuVos == null || skuVos.isEmpty()) {
            return;
        }
        Map<String, SkuSpuDisplay> displays = loadDisplaysByBusinessIds(
                skuVos.stream().map(SkuVO::getSkuId).filter(Objects::nonNull).distinct().toList());
        for (SkuVO vo : skuVos) {
            SkuSpuDisplay display = vo.getSkuId() == null ? null : displays.get(vo.getSkuId());
            vo.setSpuId(display != null && display.spuBusinessId() != null
                    ? display.spuBusinessId() : spuBusinessKey);
            if (display != null) {
                vo.setSpuName(display.spuName());
            }
        }
    }

    /**
     * 原子扣库存：SKU 不存在 → SKU_NOT_FOUND；行数 0 → STOCK_INSUFFICIENT；
     * 不限量 SKU 仅累加销量。
     *
     * <p><b>入参口径（ADR-061 §决策 2）</b>：{@code skuBusinessId} 为 <b>SKU 业务键</b> 32-hex，
     * 内部按 {@code sku.sku_id} 唯一索引解析为技术键后再走原子 SQL（性能与按技术键等价）。
     * 解析不到 → {@code SKU_NOT_FOUND}（<b>fail-closed</b>：库存是资金安全关键路径）。
     *
     * <p><b>形态非法 ≠ 不存在（批次 ③）</b>：{@code skuBusinessId} 形态非法（拼错/长度错/非 hex）
     * → {@code PARAM_ERROR(1101)} + <b>HTTP 400</b>（{@code CLIENT}），由
     * {@link #resolveTechnicalId(String, boolean)} 判定；只有「形态合法但库里没有」才是
     * {@code SKU_NOT_FOUND(3016)}。
     *
     * @param skuBusinessId SKU 业务键（适配层已归一化；此处仍做形态校验，第二道防线）
     * @param quantity      扣减数量
     * @throws BadRequestException 业务键形态非法（400 / PARAM_ERROR）
     * @throws BizException        SKU 不存在（SKU_NOT_FOUND）或库存不足（STOCK_INSUFFICIENT）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(String skuBusinessId, int quantity) {
        Long skuId = resolveTechnicalId(skuBusinessId, true);
        Sku sku = skuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        int rows = sku.isUnlimited()
                ? skuRepository.increaseSoldCount(skuId, quantity)
                : skuRepository.deductStock(skuId, quantity);
        if (rows == 0) {
            log.warn("库存扣减未命中（库存不足或已停售）: skuId={}, quantity={}", skuBusinessId, quantity);
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }
    }

    /**
     * 原子恢复库存：SKU 已删除时记 warn 后幂等返回（订单取消重试链路不抛异常）。
     *
     * <p>入参同 {@link #deductStock}（SKU 业务键）；解析不到同样按「SKU 不存在」幂等跳过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(String skuBusinessId, int quantity) {
        Long skuId = resolveTechnicalId(skuBusinessId, false);
        if (skuId == null) {
            log.warn("SKU不存在，跳过库存恢复: skuId={}, quantity={}", skuBusinessId, quantity);
            return;
        }
        Sku sku = skuRepository.findById(skuId).orElse(null);
        if (sku == null) {
            log.warn("SKU不存在，跳过库存恢复: skuId={}, quantity={}", skuBusinessId, quantity);
            return;
        }
        int rows = sku.isUnlimited()
                ? skuRepository.decreaseSoldCount(skuId, quantity)
                : skuRepository.restoreStock(skuId, quantity);
        if (rows == 0) {
            log.warn("库存恢复未命中，按幂等跳过: skuId={}, quantity={}", skuBusinessId, quantity);
        }
    }

    /**
     * SKU 业务键 → 技术键（同库只读，走 {@code sku.sku_id} 唯一索引；A 案：表结构不变）。
     *
     * <p><b>三分支（批次 ③ 拆双义错误码）</b>：
     * <ol>
     *   <li><b>形态非法</b> → 写链路（{@code failClosed=true}）抛 {@link BadRequestException}
     *       + {@code PARAM_ERROR(1101)} / <b>400</b>：这是<b>参数错误</b>，不是「SKU 不存在」；</li>
     *   <li><b>形态合法但解析不到</b> → 写链路抛 {@code SKU_NOT_FOUND(3016)}（真不存在）；</li>
     *   <li><b>恢复链路</b>（{@code failClosed=false}）：两种失败都返回 {@code null}，
     *       由调用方按幂等跳过 —— 取消/超时补偿链路<b>不得</b>因 SKU 已删而失败，
     *       更不得因上游传了脏键而阻断整批回滚。</li>
     * </ol>
     *
     * @param skuBusinessId SKU 业务键（适配层已归一化；此处仍做形态校验，第二道防线）
     * @param failClosed    {@code true} = 解析不到抛异常（扣减链路）；
     *                      {@code false} = 返回 {@code null}（恢复链路按幂等跳过）
     * @return SKU 技术键；恢复链路解析不到时返回 {@code null}
     * @throws BadRequestException 业务键形态非法且 {@code failClosed=true}（400 / PARAM_ERROR）
     * @throws BizException        SKU 不存在且 {@code failClosed=true}（SKU_NOT_FOUND）
     */
    private Long resolveTechnicalId(String skuBusinessId, boolean failClosed) {
        if (!IdGenerator.isWire(skuBusinessId)) {
            if (failClosed) {
                log.warn("SKU 业务键形态非法（400 fail-fast）: skuId={}", skuBusinessId);
                throw new BadRequestException(ErrorCode.PARAM_ERROR,
                        "sku_id 必须为 32 位 hex 业务键：形态非法属参数错误，不是「SKU 不存在」");
            }
            log.warn("SKU 业务键形态非法，恢复链路按「不存在」幂等跳过: skuId={}", skuBusinessId);
            return null;
        }
        Map<String, SkuSpuDisplay> found = loadDisplaysByBusinessIds(List.of(skuBusinessId));
        SkuSpuDisplay display = found.get(skuBusinessId);
        if (display != null && display.skuId() != null) {
            return display.skuId();
        }
        if (failClosed) {
            log.warn("SKU 业务键解析不到（SKU 不存在, fail-closed）: skuId={}", skuBusinessId);
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return null;
    }

    /** 批量在售 SKU 最低价映射（商品列表回填，禁止 N+1）。 */
    public Map<Long, Integer> getMinPriceMapBySpuIds(List<Long> spuIds) {
        return skuRepository.findOnSaleBySpuIds(spuIds).stream()
                .filter(sku -> sku.getPrice() != null)
                .collect(Collectors.toMap(
                        Sku::getSpuId,
                        Sku::getPrice,
                        Math::min));
    }

    // ==================== 私有方法 ====================

    /**
     * 按 SKU <b>业务键</b>批量取展示事实（ADR-061：入参收业务键）。
     *
     * <p>与 {@link #enrichSpuDisplay} 同为<b>展示型</b>读数，故 fail-open：查询异常 → 空 Map + WARN
     * （调用方据此返回空列表/留空字段），不抛异常。
     */
    private Map<String, SkuSpuDisplay> loadDisplaysByBusinessIds(List<String> skuBusinessIds) {
        if (skuBusinessIds == null || skuBusinessIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = skuBusinessIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, SkuSpuDisplay> displays = skuSpuDisplayQuery.findBySkuBusinessIds(ids);
            return displays == null ? Map.of() : displays;
        } catch (Exception e) {
            log.warn("SKU 业务键解析失败，返回空结果（fail-open）: skuIds={}, cause={}", ids, e.getMessage());
            return Map.of();
        }
    }

    /** 技术键 → 展示事实（enrich 阶段按 PO 技术键对齐）。 */
    private static Map<Long, SkuSpuDisplay> byTechnicalId(java.util.Collection<SkuSpuDisplay> displays) {
        Map<Long, SkuSpuDisplay> byId = new java.util.LinkedHashMap<>();
        for (SkuSpuDisplay d : displays) {
            if (d.skuId() != null) {
                byId.putIfAbsent(d.skuId(), d);
            }
        }
        return byId;
    }

    /**
     * 回填商品展示字段 + <b>两个业务键</b>（ADR-057 + ADR-058 §3 + ADR-061）。
     *
     * <p>数据源由调用方给出（{@code findBySkuIds} 或 {@code findBySkuBusinessIds} 的同一结果集），
     * 保证「入参收业务键」时<b>零额外往返</b>：一次联表查询同时得到商品列与
     * {@code SkuCO.skuId}/{@code SkuCO.spuId}。
     *
     * <p>fail-open：商品行缺失（脏数据/物理删除）→ 商品侧字段保持 null；两种情况都<b>不</b>抛异常，
     * 也不影响 SKU 本体字段 —— 购物车与下单取数不因展示字段缺失而变脆。
     */
    private void enrichSpuDisplay(List<SkuCO> cos, Map<Long, SkuSpuDisplay> displays) {
        if (cos == null || cos.isEmpty() || displays == null || displays.isEmpty()) {
            return;
        }
        for (SkuCO co : cos) {
            SkuSpuDisplay display = displays.get(co.getId());
            if (display == null) {
                continue;
            }
            co.setSkuId(display.skuBusinessId());
            co.setSpuId(display.spuBusinessId());
            co.setSpuName(display.spuName());
            co.setSellerId(display.sellerId());
            co.setCategoryId(display.categoryId());
            co.setFreightTemplateId(display.freightTemplateId());
        }
    }
}
