package org.icedamericanomall.trade.adapter.assembler;

import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 提现单装配口径单元测试 —— 商家端只回脱敏号、管理端明文 + 脱敏号都回。
 * 使用 MapStruct 生成的 {@link TradeConverterImpl} 真实实例（非打桩），保证表达式映射正确落地。
 */
@DisplayName("TradeConverter 提现单装配（商家端 vs 管理端）")
class TradeConverterTest {

    private static final String ACCOUNT = "6222021234567890123";

    private TradeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new TradeConverterImpl();
    }

    private Withdrawal entity() {
        Withdrawal e = new Withdrawal();
        e.setId(1L);
        e.setWithdrawalNo("WD-1");
        e.setSellerId(9L);
        e.setAmount(5000);
        e.setStatus(1);
        e.setBankAccount(ACCOUNT);
        e.setBankName("招商银行");
        return e;
    }

    @Test
    @DisplayName("toSellerVO — 商家端：bankAccount 置 null，bankAccountMasked 为 ****0123")
    void shouldMaskAndHidePlainAccount_whenSellerView() {
        WithdrawalVO vo = converter.toSellerVO(entity());

        assertNotNull(vo);
        assertNull(vo.getBankAccount(), "商家端不得回传明文收款账号");
        assertEquals("****0123", vo.getBankAccountMasked());
        // 业务字段不受影响
        assertEquals(1L, vo.getId());
        assertEquals(5000, vo.getAmount());
        assertEquals("招商银行", vo.getBankName());
    }

    @Test
    @DisplayName("toVO — 管理端：明文与脱敏号都返回（打款核验需要完整账号）")
    void shouldKeepPlainAndMasked_whenAdminView() {
        WithdrawalVO vo = converter.toVO(entity());

        assertNotNull(vo);
        assertEquals(ACCOUNT, vo.getBankAccount());
        assertEquals("****0123", vo.getBankAccountMasked());
    }

    @Test
    @DisplayName("null 入参 — 两个装配方法均返回 null（MapStruct 空值语义）")
    void shouldReturnNull_whenEntityNull() {
        assertNull(converter.toVO((Withdrawal) null));
        assertNull(converter.toSellerVO(null));
    }
}
