package org.icedamericanomall.wallet.app;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.wallet.domain.model.WalletTransaction;
import org.icedamericanomall.wallet.domain.repository.WalletAccountRepository;
import org.icedamericanomall.wallet.domain.repository.WalletTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("钱包编排 — 查询/扣减/入账/退款")
@ExtendWith(MockitoExtension.class)
class WalletManagerTest {

    @Mock
    private WalletAccountRepository walletAccountRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private WalletManager walletManager;

    @Test
    @DisplayName("getBalance — 无账户行返回 0")
    void shouldReturnZero_whenAccountAbsent() {
        when(walletAccountRepository.findBalanceByUserId(100L)).thenReturn(Optional.empty());
        assertEquals(0, walletManager.getBalance(100L));
    }

    @Test
    @DisplayName("deductBalance — 余额不足（条件更新未命中）→ BALANCE_INSUFFICIENT")
    void shouldThrow_whenBalanceInsufficient() {
        when(walletAccountRepository.deduct(100L, 3000)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> walletManager.deductBalance("tx-1", 100L, 3000));

        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("deductBalance — 金额非法 → BadRequestException，不打仓储")
    void shouldReject_whenAmountNonPositive() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletManager.deductBalance("tx-2", 100L, 0));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(walletAccountRepository, never()).deduct(100L, 0);
    }

    @Test
    @DisplayName("recharge — upsert 入账")
    void shouldCredit_whenRecharge() {
        walletManager.recharge(100L, 5000);
        verify(walletAccountRepository).credit(100L, 5000);
    }

    @Test
    @DisplayName("recharge — 金额非法（<=0）→ BadRequestException，不打仓储")
    void shouldRejectRecharge_whenAmountNonPositive() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletManager.recharge(100L, 0));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verify(walletAccountRepository, never()).credit(100L, 0);
    }

    // ==================== 售后退款入账（F1 / I41） ====================

    @Test
    @DisplayName("refund — 首次入账：加余额 + 写一条收入流水（bizNo=退款单号）")
    void shouldCreditAndInsertTransaction_whenFirstRefund() {
        when(walletTransactionRepository.existsByTransactionNo("RFD-1")).thenReturn(false);

        walletManager.refund("RFD-1", 100L, 12800, "RFD-1");

        verify(walletAccountRepository).credit(100L, 12800);
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).insert(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertEquals("RFD-1", transaction.transactionNo());
        assertEquals(100L, transaction.userId());
        assertEquals(1, transaction.type(), "退款为收入（type=1）");
        assertEquals(12800, transaction.amount());
        assertEquals("RFD-1", transaction.bizNo());
    }

    @Test
    @DisplayName("refund — 同一 transactionNo 重复请求：幂等成功，余额不再增加、流水不重复")
    void shouldBeIdempotent_whenSameTransactionNo() {
        when(walletTransactionRepository.existsByTransactionNo("RFD-1")).thenReturn(true);

        walletManager.refund("RFD-1", 100L, 12800, "RFD-1");

        verify(walletAccountRepository, never()).credit(100L, 12800);
        verify(walletTransactionRepository, never()).insert(any(WalletTransaction.class));
    }

    @Test
    @DisplayName("refund — 金额非正数 → BadRequestException，不打仓储")
    void shouldRejectRefund_whenAmountNonPositive() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletManager.refund("RFD-1", 100L, 0, "RFD-1"));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(walletAccountRepository);
    }

    @Test
    @DisplayName("refund — 流水号为空 → BadRequestException（幂等键不可缺失）")
    void shouldRejectRefund_whenTransactionNoBlank() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletManager.refund("  ", 100L, 100, "RFD-1"));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        verifyNoInteractions(walletAccountRepository);
    }

    @Test
    @DisplayName("refund — 并发重复（唯一键冲突）→ BizException 透出，本次事务整体回滚（不重复加余额）")
    void shouldThrow_whenConcurrentDuplicateTransactionNo() {
        when(walletTransactionRepository.existsByTransactionNo("RFD-1")).thenReturn(false);
        doThrow(new DuplicateKeyException("uk_transaction_no"))
                .when(walletTransactionRepository).insert(any(WalletTransaction.class));

        BizException ex = assertThrows(BizException.class,
                () -> walletManager.refund("RFD-1", 100L, 12800, "RFD-1"));

        assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
    }

    @Test
    @DisplayName("refund — bizNo 为空时回落 transactionNo（流水业务号不落空）")
    void shouldFallbackBizNo_whenBlank() {
        when(walletTransactionRepository.existsByTransactionNo("RFD-2")).thenReturn(false);

        walletManager.refund("RFD-2", 100L, 100, null);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).insert(captor.capture());
        assertEquals("RFD-2", captor.getValue().bizNo());
    }
}
