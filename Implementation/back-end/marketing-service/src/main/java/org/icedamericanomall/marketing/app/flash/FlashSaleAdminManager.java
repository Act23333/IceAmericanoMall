package org.icedamericanomall.marketing.app.flash;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
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

    /**
     * AD40：创建秒杀活动（时间窗推导状态落库）。
     *
     * <p>整体已过期的时间窗拒绝创建——保证「已结束」状态不可能由创建产生。
     *
     * @param req 创建请求（字段必填与正数校验已在适配层完成）
     * @return 创建后的活动视图（含回填主键与推导状态）
     * @throws BizException 时间窗无序 / 时间窗整体已过期（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public FlashSaleVO createFlashSale(FlashSaleCreateReq req) {
        FlashSale flashSale = marketingConverter.toDomain(req);
        flashSale.ensureTimeWindowOrdered();
        LocalDateTime now = LocalDateTime.now();
        ensureNotEnded(flashSale, now, "秒杀活动时间窗已整体过期，不可创建");
        flashSale.setStatus(flashSale.deriveStatus(now));
        flashSale.setSoldCount(INITIAL_SOLD_COUNT);
        flashSaleRepository.save(flashSale);
        return marketingConverter.toVO(flashSale);
    }

    /**
     * AD41：编辑秒杀活动（null-safe 局部更新 + 状态重推导）。
     *
     * @param id  活动主键
     * @param req 编辑请求（字段全可选，仅覆盖传入字段）
     * @throws BizException 活动不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）；
     *                      已结束活动不可编辑 / 合并后时间窗无序（{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFlashSale(Long id, FlashSaleUpdateReq req) {
        FlashSale flashSale = requireFlashSale(id);
        ensureNotEnded(flashSale, LocalDateTime.now(), "已结束的秒杀活动不可编辑");
        marketingConverter.mergeFlashSaleUpdate(req, flashSale);
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
