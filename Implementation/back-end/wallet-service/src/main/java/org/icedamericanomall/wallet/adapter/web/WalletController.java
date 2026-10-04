package org.icedamericanomall.wallet.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.wallet.adapter.request.WalletRechargeReq;
import org.icedamericanomall.wallet.app.WalletManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包公开端点（适配层）—— 承接原 user-service {@code /api/user/balance}（边界重构迁出）。
 *
 * <pre>
 * GET  /api/wallet            查询余额（单位：分）
 * POST /api/wallet/recharge   充值（真实支付渠道对接为后续迭代，当前直接入账）
 * </pre>
 *
 * <p>入参对象化（V5.4 结构统一）：充值参数收敛到 {@link WalletRechargeReq}，
 * 由 Spring 以属性绑定承接原查询/表单参数 {@code amount}（参数名与 HTTP 契约不变），
 * 不改为 JSON body。
 */
@Validated
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletManager walletManager;

    /** 查询当前用户余额（单位：分）。 */
    @GetMapping
    public Result<Integer> getBalance() {
        return Result.ok(walletManager.getBalance(UserContext.getUserId()));
    }

    /**
     * 充值（单位：分；查询/表单参数 {@code amount}，保持历史契约）。
     *
     * <p><b>幂等说明</b>：充值当前无幂等键，重复提交会重复入账；接入真实支付渠道时
     * 需补幂等（幂等键 + 幂等表，或支付状态机去重）。
     *
     * @param req 充值请求（amount 必填且 ≥ 1，校验失败由全局异常处理器映射为 PARAM_ERROR）
     */
    @PostMapping("/recharge")
    public Result<Integer> recharge(@Validated WalletRechargeReq req) {
        Long userId = UserContext.getUserId();
        walletManager.recharge(userId, req.getAmount());
        return Result.ok(walletManager.getBalance(userId));
    }
}
