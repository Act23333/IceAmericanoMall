package org.icedamericanomall.wallet.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.wallet.adapter.request.RateUpdateReq;
import org.icedamericanomall.wallet.adapter.request.StatusUpdateReq;
import org.icedamericanomall.wallet.adapter.response.CurrencyAdminVO;
import org.icedamericanomall.wallet.app.currency.CurrencyAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台币种/汇率维护接口（V5.17 AD66-AD68，契约 §4.13.8g；F16 多币种 Phase 1）。
 *
 * <p>控制器只做参数校验与委托：联合分页 / 人工修正汇率 / 启停编排下沉到
 * {@link CurrencyAdminManager}。网关侧 {@code /api/admin/currency/**} 须先于
 * {@code /api/admin/**} 兜底路由声明（O7，ADR-035/036 先例）。
 *
 * <p><b>审计（ADR-032 管道一）</b>：两个写操作均标注 {@code @OperationLog}（module=CURRENCY），
 * 产出单行 JSON 审计日志（logger {@code AUDIT}）；默认不记参数值（PII 安全默认），
 * 汇率留痕以 source/effect_time/updater_id 落库承载。
 */
@RestController
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequestMapping("/api/admin/currency")
@RequiredArgsConstructor
public class AdminCurrencyController {

    /** 审计模块：资金域币种/汇率。 */
    private static final String MODULE_CURRENCY = "CURRENCY";

    /** 审计动作：人工修正汇率。 */
    private static final String ACTION_UPDATE_RATE = "UPDATE_RATE";

    /** 审计动作：币种启停。 */
    private static final String ACTION_UPDATE_STATUS = "UPDATE_STATUS";

    private final CurrencyAdminManager currencyAdminManager;

    /** AD66：币种 + 汇率联合分页（status 过滤可选；page 缺省 1，size 缺省 20）。 */
    @GetMapping("/page")
    public Result<IPage<CurrencyAdminVO>> page(@RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.ok(currencyAdminManager.page(status, page, size));
    }

    /** AD67：人工修正汇率（source=MANUAL、effectTime=now、updaterId 取 UserContext；定点整数直传）。 */
    @OperationLog(module = MODULE_CURRENCY, action = ACTION_UPDATE_RATE, description = "人工修正汇率")
    @PutMapping("/{code}/rate")
    public Result<Void> updateRate(@PathVariable String code, @RequestBody @Valid RateUpdateReq req) {
        currencyAdminManager.updateRate(code, req);
        return Result.ok();
    }

    /** AD68：启用/停用币种（停用不影响既有订单快照展示——仅影响 C 端列表与 I61）。 */
    @OperationLog(module = MODULE_CURRENCY, action = ACTION_UPDATE_STATUS, description = "币种启停")
    @PutMapping("/{code}/status")
    public Result<Void> updateStatus(@PathVariable String code, @RequestBody @Valid StatusUpdateReq req) {
        currencyAdminManager.updateStatus(code, req);
        return Result.ok();
    }
}
