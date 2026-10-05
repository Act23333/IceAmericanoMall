package org.icedamericanomall.marketing.adapter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.adapter.response.PromotionVO;
import org.icedamericanomall.marketing.app.promotion.PromotionAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台促销活动接口（V5.8 AD48-AD50，F5 满减/满折促销引擎，ADR-043）。
 *
 * <p>控制器只做参数校验与委托：创建/编辑/删除编排（时间窗状态推导、null-safe 合并、
 * 进行中禁删 / 已结束禁编守卫）下沉到 {@link PromotionAdminManager}。
 *
 * <p><b>审计（ADR-032 管道一）</b>：三个写操作均标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}）；创建/编辑请求体含价格明细，
 * 默认不记参数值（PII 安全默认），删除端点开启参数摘要（仅活动 id）。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/promotion")
@RequiredArgsConstructor
public class AdminPromotionController {

    /** 审计模块：营销域促销活动。 */
    private static final String MODULE_MARKETING_PROMOTION = "MARKETING_PROMOTION";

    /** 审计动作：创建促销活动。 */
    private static final String ACTION_CREATE_PROMOTION = "CREATE_PROMOTION";

    /** 审计动作：编辑促销活动。 */
    private static final String ACTION_UPDATE_PROMOTION = "UPDATE_PROMOTION";

    /** 审计动作：删除促销活动。 */
    private static final String ACTION_DELETE_PROMOTION = "DELETE_PROMOTION";

    private final PromotionAdminManager promotionAdminManager;

    /** AD48：创建促销活动（活动状态由时间窗推导落库：1未开始/2进行中）。 */
    @OperationLog(module = MODULE_MARKETING_PROMOTION, action = ACTION_CREATE_PROMOTION, description = "创建促销活动")
    @PostMapping
    public Result<PromotionVO> create(@RequestBody @Valid PromotionCreateReq req) {
        return Result.ok(promotionAdminManager.createPromotion(req));
    }

    /** AD49：编辑促销活动（null-safe 局部更新 + 状态重推导；已结束活动不可编辑 → 400）。 */
    @OperationLog(module = MODULE_MARKETING_PROMOTION, action = ACTION_UPDATE_PROMOTION, description = "编辑促销活动")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid PromotionUpdateReq req) {
        promotionAdminManager.updatePromotion(id, req);
        return Result.ok();
    }

    /** AD50：删除促销活动（进行中禁删 → 400；物理删除，表无逻辑删除列）。 */
    @OperationLog(module = MODULE_MARKETING_PROMOTION, action = ACTION_DELETE_PROMOTION, description = "删除促销活动",
            recordParams = true)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promotionAdminManager.deletePromotion(id);
        return Result.ok();
    }
}
