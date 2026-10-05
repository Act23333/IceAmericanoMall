package org.icedamericanomall.marketing.adapter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台拼团活动接口（V5.14 AD54-AD56，F13 拼团，ADR-049）。
 *
 * <p>控制器只做参数校验与委托：创建/编辑/删除编排（时间窗状态推导、null-safe 合并、
 * 进行中禁删 / 已结束禁编 / 已过期窗口拒建守卫）下沉到 {@link GroupBuyAdminManager}。
 *
 * <p><b>审计（ADR-032 管道一）</b>：三个写操作均标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}）；创建/编辑请求体含价格明细，
 * 默认不记参数值（PII 安全默认），删除端点开启参数摘要（仅活动 id）。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/group-buy")
@RequiredArgsConstructor
public class AdminGroupBuyController {

    /** 审计模块：营销域拼团活动。 */
    private static final String MODULE_MARKETING_GROUP_BUY = "MARKETING_GROUP_BUY";

    /** 审计动作：创建拼团活动。 */
    private static final String ACTION_CREATE_GROUP_BUY = "CREATE_GROUP_BUY";

    /** 审计动作：编辑拼团活动。 */
    private static final String ACTION_UPDATE_GROUP_BUY = "UPDATE_GROUP_BUY";

    /** 审计动作：删除拼团活动。 */
    private static final String ACTION_DELETE_GROUP_BUY = "DELETE_GROUP_BUY";

    private final GroupBuyAdminManager groupBuyAdminManager;

    /** AD54：创建拼团活动（活动状态由时间窗推导落库：1未开始/2进行中）。 */
    @OperationLog(module = MODULE_MARKETING_GROUP_BUY, action = ACTION_CREATE_GROUP_BUY, description = "创建拼团活动")
    @PostMapping
    public Result<GroupBuyActivityVO> create(@RequestBody @Valid GroupBuyCreateReq req) {
        return Result.ok(groupBuyAdminManager.createActivity(req));
    }

    /** AD55：编辑拼团活动（null-safe 局部更新 + 状态重推导；已结束活动不可编辑 → 400）。 */
    @OperationLog(module = MODULE_MARKETING_GROUP_BUY, action = ACTION_UPDATE_GROUP_BUY, description = "编辑拼团活动")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid GroupBuyUpdateReq req) {
        groupBuyAdminManager.updateActivity(id, req);
        return Result.ok();
    }

    /** AD56：删除拼团活动（进行中禁删 → 400；物理删除，表无逻辑删除列）。 */
    @OperationLog(module = MODULE_MARKETING_GROUP_BUY, action = ACTION_DELETE_GROUP_BUY, description = "删除拼团活动",
            recordParams = true)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        groupBuyAdminManager.deleteActivity(id);
        return Result.ok();
    }
}
