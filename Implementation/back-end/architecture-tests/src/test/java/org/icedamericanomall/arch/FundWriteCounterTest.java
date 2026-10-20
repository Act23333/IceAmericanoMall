package org.icedamericanomall.arch;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金写计数口径的单测（六条合成输入）—— **在 JavaParser 的 body 上重跑**（A2 验收）。
 *
 * <p><b>为什么单独测计数</b>：{@code FundTransactionGuardTest} 的计数曾用「调用点前 200 字符窗口」，
 * 窗口会吃进**前一次调用**的 receiver ⇒ {@code payByBalance} 被数成 5（真值 3），
 * 而 {@code WalletManager#refund} 可能被数少。窗口是「位置无关的近似」，必须换成 **receiver 判定**。
 *
 * <p><b>为什么要换到 AST 上重跑</b>：换成 receiver 判定后仍是**在文本上**做正则，
 * 于是「跨行书写 / 链式调用 / 字符串里恰好写着 `repo.insert(` 」这些形态继续靠运气。
 * 现在 receiver 来自 AST 的 {@code MethodCallExpr#getScope}：是不是「单个标识符」由语法树回答，
 * 不再由正则猜。
 *
 * <p><b>口径定义（唯一）</b>：资金写 = **资金仓储 receiver + 写动词**。
 * <ul>
 *   <li>receiver 必须是**单个标识符**（{@code walletAccountRepository}），且它本身匹配资金命名；</li>
 *   <li>动词必须是写动词（前缀集合：insert/save/update/delete/deduct/credit/mark/create/cancel…）；</li>
 *   <li>查询动词（exists/select/find/get/count）**不算**写；</li>
 *   <li>链式调用的 receiver 是另一个调用 ⇒ 不算；</li>
 *   <li>实体对象（{@code payOrder}）不是仓储 ⇒ 不算。</li>
 * </ul>
 * 多行写法（receiver 与 `.动词(` 跨行）**仍要算** —— 这是本仓常态代码风格，也是旧实现的痛点。
 */
@DisplayName("资金写计数 — 只认「资金仓储 receiver + 写动词」，六条合成输入（AST）")
class FundWriteCounterTest {

    /**
     * 把「方法体」包成一个可解析的类，取第一个方法，按 AST 计数。
     *
     * <p>刻意**不复用**任何生产解析路径的捷径：这里要验的正是「从源码到计数」这一步，
     * 一旦写成「手工构造 AST 节点」，就绕开了「解析器把这段文字切成什么」这个真问题。
     */
    private static int countIn(String methodBody) {
        String source = "class S {\n"
                + "    Object walletAccountRepository;\n"
                + "    Object walletTransactionRepository;\n"
                + "    Object payOrderRepository;\n"
                + "    Object payOrderService;\n"
                + "    Object channelRouter;\n"
                + "    Object payOrder;\n"
                + "    Object getWalletAccountRepository() { return null; }\n"
                + "    void m() " + methodBody + "\n"
                + "}\n";
        MethodDeclaration method = JavaSourceParser.methods(JavaSourceParser.parse(source)).stream()
                .filter(m -> m.getNameAsString().equals("m"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("样本方法 m 未被解析出来（解析器退化）"));
        return FundTransactionGuardTest.countFundWrites(method);
    }

    @Test
    @DisplayName("① 资金仓储 + 写动词（deduct）→ 算 1")
    void shouldCountDeductOnFundRepository() {
        assertThat(countIn("{ walletAccountRepository.deduct(userId, amount); }")).isEqualTo(1);
    }

    @Test
    @DisplayName("② 查询动词（existsByTransactionNo）→ 不算")
    void shouldNotCountQueryVerb() {
        assertThat(countIn("{ walletTransactionRepository.existsByTransactionNo(tx); }")).isZero();
    }

    @Test
    @DisplayName("③ 链式调用（返回对象再插入）→ 不算（receiver 不是单个标识符）")
    void shouldNotCountChainedCall() {
        assertThat(countIn("{ getWalletAccountRepository().credit(userId, amount); }")).isZero();
    }

    @Test
    @DisplayName("④ 多行写法（receiver 与 .动词( 跨行）→ 仍算 1")
    void shouldCountMultiLineCall() {
        assertThat(countIn("{\n"
                + "        walletTransactionRepository\n"
                + "                .insert(tx);\n"
                + "    }")).isEqualTo(1);
    }

    @Test
    @DisplayName("⑤ 实体 receiver（payOrder.markPaid）→ 不算（实体不是仓储）")
    void shouldNotCountEntityReceiver() {
        assertThat(countIn("{ payOrder.markPaid(LocalDateTime.now()); }")).isZero();
    }

    @Test
    @DisplayName("★ 回归 — 连续多个调用不得串味（窗口计数的病根）")
    void shouldNotLeakPreviousReceiver() {
        assertThat(countIn("{\n"
                + "        Object o = payOrderService.createPending(orderNo);\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "        payOrderService.markBalancePaidIfPending(o);\n"
                + "    }"))
                .as("payOrderService 的两次写 + channelRouter 的 Feign 不计 ⇒ 2；多算即为串味")
                .isEqualTo(2);
    }
}
