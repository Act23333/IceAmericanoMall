package org.icedamericanomall.wallet.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.wallet.adapter.request.BalanceRefundReq;
import org.icedamericanomall.wallet.app.WalletManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 钱包内部端点（适配层）—— 仅集群内 Feign 调用（ia-api {@code BalanceClient} 契约）。
 *
 * <p>路径 {@code /internal/balance} 与迁出前完全一致，调用方（pay-service 余额扣款/退款）
 * 只感知服务名变化，契约零改动。{@code /internal/**} 不包装 {@code Result}，
 * 业务异常以原始 HTTP 状态 + 极简 {@code {"code","msg"}} 透出（ADR-004）。
 *
 * <pre>
 * POST /internal/balance/deduct   余额扣款（原路径，查询参数口径，保持不变）
 * POST /internal/balance/refund   余额退款入账（I41，JSON body，transaction_no 幂等）
 * </pre>
 */
@RestController
@RequestMapping("/internal/balance")
@RequiredArgsConstructor
public class InternalWalletController {

    private final WalletManager walletManager;

    /** 原子扣减余额（余额支付）。 */
    @PostMapping("/deduct")
    public void deductBalance(@RequestParam Long userId, @RequestParam Integer amount) {
        walletManager.deductBalance(userId, amount);
    }

    /**
     * 余额退款入账（F1 余额渠道原路退回，I41）。
     *
     * <p>重复请求（同 {@code transaction_no}）按幂等成功返回，不重复加余额。
     *
     * @param req 入账参数（流水号 / 用户 / 金额（分）/ 业务号），字段名校验失败 → PARAM_ERROR(400)
     */
    @PostMapping("/refund")
    public void refund(@Valid @RequestBody BalanceRefundReq req) {
        walletManager.refund(req.getTransactionNo(), req.getUserId(), req.getAmount(), req.getBizNo());
    }
}
