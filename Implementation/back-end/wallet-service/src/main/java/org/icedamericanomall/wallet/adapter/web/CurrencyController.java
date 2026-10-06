package org.icedamericanomall.wallet.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.wallet.adapter.response.CurrencyVO;
import org.icedamericanomall.wallet.app.currency.CurrencyQueryManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 币种公开端点（适配层，🌐 匿名可访问——网关 {@code /api/currency/**} GET 白名单）。
 *
 * <p>F16 多币种展示数据面（契约 §4.2.11 P36）：仅启用币种 + 当前定点整数汇率，
 * 供前端本币展示换算（换算在前端执行，D7-A）；<b>响应不含免责文案</b>（前端静态常量，D6）。
 * 全链路 fail-open：列表为空/汇率未配置时前端降级仅 CNY 展示（US-CUR-7）。
 *
 * <pre>
 * GET /api/currency/list   启用币种 + 当前汇率（60s 本地缓存，I44 先例）
 * </pre>
 */
@RestController
@RequestMapping("/api/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyQueryManager currencyQueryManager;

    /** P36：启用币种 + 当前汇率列表（仅启用；本地表直读 + 60s 缓存）。 */
    @GetMapping("/list")
    public Result<List<CurrencyVO>> list() {
        return Result.ok(currencyQueryManager.listEnabled());
    }
}
