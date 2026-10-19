package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金写计数口径的单测（五条合成输入）。
 *
 * <p><b>为什么单独测计数</b>：{@code FundTransactionGuardTest} 的计数曾用"调用点前 200 字符窗口"，
 * 窗口会吃进**前一次调用**的 receiver ⇒ `payByBalance` 被数成 5（真值应为 3），
 * 而 `WalletManager#refund` 可能被数少。窗口是"位置无关的近似"，必须换成**receiver 判定**。
 *
 * <p><b>口径定义（唯一）</b>：资金写 = **资金仓储 receiver + 写动词**。
 * <ul>
 *   <li>receiver 必须是**单个标识符**（`walletAccountRepository`），且它本身匹配资金命名；</li>
 *   <li>动词必须在写动词集合内（insert/save/update/delete/deduct/credit/mark/create/cancel/void/bind…）；</li>
 *   <li>查询动词（exists/select/find/get/count）**不算**写；</li>
 *   <li>链式调用的 receiver 是 `)` 或非资金标识符 ⇒ 不算；</li>
 *   <li>实体对象（`payOrder`）不是仓储 ⇒ 不算。</li>
 * </ul>
 * 多行写法（receiver 与 `.动词(` 跨行）**仍要算** —— 这是上一条"20 行以内"的代码风格常态。
 */
@DisplayName("资金写计数 — 只认「资金仓储 receiver + 写动词」，五条合成输入")
class FundWriteCounterTest {

    @Test
    @DisplayName("① 资金仓储 + 写动词（deduct）→ 算 1")
    void shouldCountDeductOnFundRepository() {
        String body = "{ walletAccountRepository.deduct(userId, amount); }";
        assertThat(FundTransactionGuardTest.countFundWrites(body)).isEqualTo(1);
    }

    @Test
    @DisplayName("② 查询动词（existsByTransactionNo）→ 不算")
    void shouldNotCountQueryVerb() {
        String body = "{ walletTransactionRepository.existsByTransactionNo(tx); }";
        assertThat(FundTransactionGuardTest.countFundWrites(body)).isZero();
    }

    @Test
    @DisplayName("③ 链式调用（返回对象再插入）→ 不算（receiver 不是资金仓储标识符）")
    void shouldNotCountChainedCall() {
        String body = "{ getWalletAccountRepository().credit(userId, amount); }";
        assertThat(FundTransactionGuardTest.countFundWrites(body)).isZero();
    }

    @Test
    @DisplayName("④ 多行写法（receiver 与 .动词( 跨行）→ 仍算 1")
    void shouldCountMultiLineCall() {
        String body = "{\n"
                + "        walletTransactionRepository\n"
                + "                .insert(tx);\n"
                + "    }";
        assertThat(FundTransactionGuardTest.countFundWrites(body)).isEqualTo(1);
    }

    @Test
    @DisplayName("⑤ 实体 receiver（payOrder.markPaid）→ 不算（实体不是仓储）")
    void shouldNotCountEntityReceiver() {
        String body = "{ payOrder.markPaid(LocalDateTime.now()); }";
        assertThat(FundTransactionGuardTest.countFundWrites(body)).isZero();
    }

    @Test
    @DisplayName("★ 回归 — 连续多个调用不得串味（窗口计数的病根）")
    void shouldNotLeakPreviousReceiver() {
        String body = "{\n"
                + "        PayOrder o = payOrderService.createPending(orderNo);\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "        payOrderService.markBalancePaidIfPending(o.getId());\n"
                + "    }";
        assertThat(FundTransactionGuardTest.countFundWrites(body))
                .as("payOrderService 的两次写 + channelRouter 的 Feign 不计 ⇒ 2；多算即为窗口串味")
                .isEqualTo(2);
    }
}
