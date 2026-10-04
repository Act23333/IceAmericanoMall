package org.icedamericanomall.trade.app.finance;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverter;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提现用例族编排（应用层）。
 *
 * <pre>
 * Scenario: 商家申请提现
 *   Given 提现额大于 0 且不超过可用余额（已结算未打款金额）
 *   When 提交申请
 *   Then 落库"待审核"提现单
 * Scenario: 金额非法 / 超出可用余额 → PARAM_ERROR / BALANCE_INSUFFICIENT
 * Scenario: 管理员审核非法状态值 → PARAM_ERROR
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class WithdrawalManager {

    private final WithdrawalRepository withdrawalRepository;
    private final SettlementManager settlementManager;
    private final TradeConverter tradeConverter;

    /** 商家提现单分页（创建时间倒序）。 */
    public IPage<WithdrawalVO> pageBySeller(Long sellerId, int page, int size) {
        return withdrawalRepository.pageBySeller(sellerId, page, size)
                .convert(tradeConverter::toSellerVO);
    }

    /** 管理端提现单分页（可按状态过滤；管理端保留明文账号打款核验）。 */
    public IPage<WithdrawalVO> pageByStatus(Integer status, int page, int size) {
        return withdrawalRepository.pageByStatus(status, page, size).convert(tradeConverter::toVO);
    }

    /** 商家申请提现。 */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawalVO applyWithdrawal(Long sellerId, WithdrawalApplyReq req) {
        Integer amount = req.getAmount();
        if (amount == null || amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提现金额必须大于 0");
        }
        int available = settlementManager.availableBalance(sellerId);
        if (amount > available) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "提现金额超过可用余额");
        }
        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setSellerId(sellerId);
        withdrawal.setWithdrawalNo(IdUtil.fastSimpleUUID());
        withdrawal.setAmount(amount);
        withdrawal.setBankAccount(req.getBankAccount());
        withdrawal.setBankName(req.getBankName());
        withdrawal.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        return tradeConverter.toSellerVO(withdrawalRepository.save(withdrawal));
    }

    /** 管理员审核提现；打款成功时级联更新结算单为已打款。 */
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        WithdrawalStatusEnum target = WithdrawalStatusEnum.of(status);
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法提现状态: " + status);
        }
        Withdrawal withdrawal = withdrawalRepository.findById(id);
        if (withdrawal == null) {
            throw new BizException(ErrorCode.WITHDRAWAL_NOT_FOUND, "提现申请不存在");
        }
        withdrawal.setStatus(target.getCode());
        withdrawal.setAdminRemark(adminRemark);
        withdrawalRepository.update(withdrawal);
        // 审核通过（已打款）→ 级联将该商家已结算单据置为已打款
        if (target == WithdrawalStatusEnum.PAID_OUT) {
            settlementManager.markSellerSettlementsPaidOut(withdrawal.getSellerId());
        }
    }
}
