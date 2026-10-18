package org.icedamericanomall.marketing.adapter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.app.flash.FlashSaleAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台秒杀活动接口（V5.7 AD40-AD42）。
 *
 * <p>控制器只做参数校验与委托：创建/编辑/删除编排（时间窗状态推导、null-safe 合并、
 * 进行中禁删 / 已结束禁编守卫）下沉到 {@link FlashSaleAdminManager}；
 * FlashSaleVO 复用 M8 列表口径（{@code adapter/response/FlashSaleVO}，不扩契约）。
 *
 * <p><b>审计（ADR-032 管道一）</b>：三个写操作均标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}）；创建/编辑请求体含商品与价格明细，
 * 默认不记参数值（PII 安全默认），删除端点开启参数摘要（仅活动 id）。
 */
@RestController
// 权限位拆分（5a 批 7，2026-10-18）：秒杀活动管理。**注意**：`FlashSaleAdminManager#deleteFlashSale` 对进行中活动直接抛错（禁删）；
//   若将来放开"删除进行中活动"，需重新评估权限位 —— 那会间接影响已下单用户的履约/退款，
//   与拼团那边（批 7 复核已加"有未结团团禁删"）同源
@PreAuthorize("@ss.hasPermi('marketing:admin')")
@RequestMapping("/api/admin/flash")
@RequiredArgsConstructor
public class AdminFlashController {

    /** 审计模块：营销域秒杀活动。 */
    private static final String MODULE_MARKETING_FLASH = "MARKETING_FLASH";

    /** 审计动作：创建秒杀活动。 */
    private static final String ACTION_CREATE_FLASH_SALE = "CREATE_FLASH_SALE";

    /** 审计动作：编辑秒杀活动。 */
    private static final String ACTION_UPDATE_FLASH_SALE = "UPDATE_FLASH_SALE";

    /** 审计动作：删除秒杀活动。 */
    private static final String ACTION_DELETE_FLASH_SALE = "DELETE_FLASH_SALE";

    private final FlashSaleAdminManager flashSaleAdminManager;

    /** AD40：创建秒杀活动（活动状态由时间窗推导落库：1未开始/2进行中）。 */
    @OperationLog(module = MODULE_MARKETING_FLASH, action = ACTION_CREATE_FLASH_SALE, description = "创建秒杀活动")
    @PostMapping
    public Result<FlashSaleVO> create(@RequestBody @Valid FlashSaleCreateReq req) {
        return Result.ok(flashSaleAdminManager.createFlashSale(req));
    }

    /** AD41：编辑秒杀活动（null-safe 局部更新 + 状态重推导；已结束活动不可编辑 → 400）。 */
    @OperationLog(module = MODULE_MARKETING_FLASH, action = ACTION_UPDATE_FLASH_SALE, description = "编辑秒杀活动")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid FlashSaleUpdateReq req) {
        flashSaleAdminManager.updateFlashSale(id, req);
        return Result.ok();
    }

    /** AD42：删除秒杀活动（进行中禁删 → 400；物理删除，表无逻辑删除列）。 */
    @OperationLog(module = MODULE_MARKETING_FLASH, action = ACTION_DELETE_FLASH_SALE, description = "删除秒杀活动",
            recordParams = true)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        flashSaleAdminManager.deleteFlashSale(id);
        return Result.ok();
    }
}
