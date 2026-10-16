package org.icedamericanomall.marketing.app.flash;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.repository.FlashSaleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 秒杀活动管理端用例族编排（应用层 · 平台后台，V5.7 AD40-AD42）。
 *
 * <p>与 {@link FlashSaleManager}（前台抢购/异步统计）按用例族拆分（ADR-025，
 * 镜像 {@code CouponAdminManager} 先例）。三个用例均为纯本地 DB 操作，
 * 事务边界在本层（{@code rollbackFor} 全量回滚）。
 *
 * <p><b>状态口径</b>：活动状态一律由时间窗实时推导（{@code FlashSale.deriveStatus}），
 * 落库 {@code status} 仅是写入时刻快照；已结束禁编 / 进行中禁删守卫不信任快照。
 *
 * <p><b>Redis 库存预热选型</b>：创建/编辑后不走显式预热（{@code FlashStockStore.preloadStock}
 * 为覆盖写，编辑进行中活动会把已扣库存重置回满），沿用契约 V5.7 注定稿口径——
 * 依赖 {@code RedisFlashStockStore.ensureStockInitialized} 按 DB 剩余库存惰性兜底重建。
 */
@Component
@RequiredArgsConstructor
public class FlashSaleAdminManager {

    /** 秒杀初始已售数（表默认值显式化，避免依赖隐式默认）。 */
    private static final int INITIAL_SOLD_COUNT = 0;

    private final FlashSaleRepository flashSaleRepository;
    private final MarketingConverter marketingConverter;
    private final FlashSaleViewManager flashSaleViewManager;

    /**
     * AD40：创建秒杀活动（时间窗推导状态落库）。
     *
     * <p>整体已过期的时间窗拒绝创建——保证「已结束」状态不可能由创建产生。
     *
     * <p><b>入参口径（契约 §1.8② + ADR-061 §决策 2）</b>：{@code spu_id}/{@code sku_id} 收
     * <b>业务键</b> 32-hex，写入 {@code flash_sale} 前经读模型解析为技术键（A 案：该表保持
     * 技术键不动）；解析不到 → {@code PARAM_ERROR}（fail-fast，不落半条脏数据）。
     *
     * <p>回执复用 {@link FlashSaleViewManager}（与 M8 列表同一装配口径）：业务键/商品名/主图/
     * 原价与进度百分比一并回填，fail-open 不回退创建结果；读模型为本地只读查询，不违反
     * 「事务内禁止 Feign」。
     *
     * @param req 创建请求（字段必填与正数校验已在适配层完成）
     * @return 创建后的活动视图（含回填主键与推导状态）
     * @throws BizException 时间窗无序 / 时间窗整体已过期（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public FlashSaleVO createFlashSale(FlashSaleCreateReq req) {
        FlashSale flashSale = marketingConverter.toDomain(req);
        applyResolvedKeys(flashSale, req.getSpuId(), req.getSkuId());
        flashSale.ensureTimeWindowOrdered();
        LocalDateTime now = LocalDateTime.now();
        ensureNotEnded(flashSale, now, "秒杀活动时间窗已整体过期，不可创建");
        flashSale.setStatus(flashSale.deriveStatus(now));
        flashSale.setSoldCount(INITIAL_SOLD_COUNT);
        flashSaleRepository.save(flashSale);
        return flashSaleViewManager.toView(flashSale);
    }

    /**
     * AD41：编辑秒杀活动（null-safe 局部更新 + 状态重推导）。
     *
     * <p>{@code spu_id}/{@code sku_id}（可选、业务键）同样先解析再合并 —— 二者同名异义
     * （请求侧业务键 vs 聚合侧技术键），MapStruct 已 ignore，由本类显式赋值（ADR-058 §4）。
     *
     * @param id  活动主键
     * @param req 编辑请求（字段全可选，仅覆盖传入字段）
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      已结束活动不可编辑 / 合并后时间窗无序 / 业务键解析失败（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFlashSale(Long id, FlashSaleUpdateReq req) {
        FlashSale flashSale = requireFlashSale(id);
        ensureNotEnded(flashSale, LocalDateTime.now(), "已结束的秒杀活动不可编辑");
        marketingConverter.mergeFlashSaleUpdate(req, flashSale);
        // 仅当请求真的传了键才覆盖（null = 不更新，与其余字段的 null-safe 语义一致）
        if (req.getSkuId() != null || req.getSpuId() != null) {
            applyResolvedKeys(flashSale, req.getSpuId(), req.getSkuId());
        }
        flashSale.ensureTimeWindowOrdered();
        flashSale.setStatus(flashSale.deriveStatus(LocalDateTime.now()));
        flashSaleRepository.update(flashSale);
    }

    /**
     * AD42：删除秒杀活动（物理删除，表无逻辑删除列）。
     *
     * @param id 活动主键
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      进行中活动禁删（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFlashSale(Long id) {
        FlashSale flashSale = requireFlashSale(id);
        if (flashSale.deriveStatus(LocalDateTime.now()) == FlashSaleStatusEnum.ACTIVE.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "进行中的秒杀活动禁止删除");
        }
        flashSaleRepository.deleteById(id);
    }

    // ==================== 内部方法 ====================

    /**
     * SPU/SKU <b>业务键</b> → 技术键并写入聚合（ADR-061 §决策 2 + A 案）。
     *
     * <p>解析走 {@code sku.sku_id} 唯一索引（本地只读，无 Feign，事务安全）；解析不到即
     * {@code PARAM_ERROR} —— 写路径必须 fail-fast，不得落一条「指向不存在 SKU」的活动。
     * {@code spuId} 以 <b>SKU 行所属 SPU</b> 为准（入参 SPU 仅作一致性校验），
     * 避免「SKU 与 SPU 不匹配」的脏数据。
     *
     * @throws BizException 业务键格式非法 / SKU 不存在 / SPU 与 SKU 不匹配
     */
    private void applyResolvedKeys(FlashSale flashSale, String spuBusinessId, String skuBusinessId) {
        FlashSaleSpuBrief brief = flashSaleViewManager.findBySkuBusinessId(skuBusinessId);
        if (brief == null || brief.skuId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SKU 不存在（sku_id 业务键无效）");
        }
        if (spuBusinessId != null && brief.spuBusinessId() != null
                && !spuBusinessId.equals(brief.spuBusinessId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "SPU 与 SKU 不匹配");
        }
        // 技术键一律取自读模型解析结果（SKU 行所属 SPU 为准），不采信入参技术键
        flashSale.setSkuId(brief.skuId());
        flashSale.setSpuId(brief.spuId());
    }

    /** 按主键取活动，不存在抛 NO_RESOURCE_FOUND（AD41/AD42 契约错误口径 1103）。 */
    private FlashSale requireFlashSale(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id);
        if (flashSale == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "秒杀活动不存在: " + id);
        }
        return flashSale;
    }

    /** 已结束守卫（时间窗实时推导，不信任落库 status 快照）。 */
    private void ensureNotEnded(FlashSale flashSale, LocalDateTime now, String message) {
        if (flashSale.deriveStatus(now) == FlashSaleStatusEnum.ENDED.getCode()) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }
}
