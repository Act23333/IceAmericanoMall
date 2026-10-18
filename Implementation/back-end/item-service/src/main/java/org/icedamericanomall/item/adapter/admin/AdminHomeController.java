package org.icedamericanomall.item.adapter.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.item.adapter.request.HomeConfigReq;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.app.HomeConfigManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台 — 首页装修配置。
 *
 * <p><b>审计（ADR-032 管道一）</b>：三个写操作标注 {@code @OperationLog}，产出单行 JSON 审计日志
 * （logger {@code AUDIT}，经 ELK 采集，不落库）；含请求体的端点默认不记参数值（PII 安全默认），
 * 仅路径标识的删除端点开启参数摘要。
 */
@RestController
// 权限位拆分（5a，2026-10-18）：首页装修（平台级配置）
@PreAuthorize("@ss.hasPermi('home:admin')")
@RequestMapping("/api/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {

    /** 审计模块：商品域首页装修。 */
    private static final String MODULE_ITEM_HOME = "ITEM_HOME";

    /** 审计动作：新增首页配置。 */
    private static final String ACTION_CREATE_HOME_CONFIG = "CREATE_HOME_CONFIG";

    /** 审计动作：更新首页配置。 */
    private static final String ACTION_UPDATE_HOME_CONFIG = "UPDATE_HOME_CONFIG";

    /** 审计动作：删除首页配置。 */
    private static final String ACTION_DELETE_HOME_CONFIG = "DELETE_HOME_CONFIG";

    private final HomeConfigManager homeConfigManager;

    @OperationLog(module = MODULE_ITEM_HOME, action = ACTION_CREATE_HOME_CONFIG, description = "新增首页配置")
    @PostMapping
    public Result<HomeConfigVO> create(@Valid @RequestBody HomeConfigReq req) {
        return Result.ok(homeConfigManager.createConfig(req));
    }

    @OperationLog(module = MODULE_ITEM_HOME, action = ACTION_UPDATE_HOME_CONFIG, description = "更新首页配置")
    @PutMapping("/{id}")
    public Result<HomeConfigVO> update(@PathVariable Long id, @Valid @RequestBody HomeConfigReq req) {
        return Result.ok(homeConfigManager.updateConfig(id, req));
    }

    @OperationLog(module = MODULE_ITEM_HOME, action = ACTION_DELETE_HOME_CONFIG, description = "删除首页配置",
            recordParams = true)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        homeConfigManager.deleteConfig(id);
        return Result.ok();
    }

    @GetMapping
    public Result<List<HomeConfigVO>> list() {
        return Result.ok(homeConfigManager.listAll());
    }
}
