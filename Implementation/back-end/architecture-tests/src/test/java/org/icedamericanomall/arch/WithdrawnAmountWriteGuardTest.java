package org.icedamericanomall.arch;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫：{@code settlement.withdrawn_amount} 的写入形状与<b>唯一减点</b>。
 *
 * <p><b>判据按「SQL 归属」，不按方法名</b>（用户点名）：检查对象是**语句本身**
 * （{@code UPDATE settlement ... SET withdrawn_amount ...}），它属于哪张表、哪一列、
 * 有没有边界条件，全都写在 SQL 里。方法名可以随便改、可以加成参数重载、可以被移动包，
 * 但「这段 SQL 会不会把已核销额改成负数/写成常数」是 SQL 的属性 —— 所以只看 SQL。
 *
 * <p>三条规则：
 * <ol>
 *   <li><b>只能自增自减</b>：{@code SET withdrawn_amount = withdrawn_amount ± #{}}。
 *       写成常数（{@code = 0}）或直接赋值（{@code = #{amount}}）都会绕过「不许超过结算额」这条不变量
 *       —— 前者把已核销额抹平，后者可以凭空设成任意值。</li>
 *   <li><b>必须带边界</b>：加要么带 {@code withdrawn_amount + #{} <= settlement_amount}，
 *       减要么带 {@code withdrawn_amount >= #{}}。没有边界就等于允许负数/超核销。</li>
 *   <li><b>减点唯一</b>：全仓只允许**一处** SQL 做减法（释放路径）。多出来的减点通常意味着
 *       有人又写了第二条释放/回退路径 —— 那正是 E4/E5 等式失配的来源。</li>
 * </ol>
 *
 * <p>已知局限（登记，不假装覆盖）：拼字符串拼接的 SQL、XML 里的 SQL、存储过程、直接 {@code JdbcTemplate}
 * 手写语句 —— 本守卫只看 Java 源码里的<b>字符串字面量</b>。真要用那些写法，属于改架构，需另立守卫。
 */
class WithdrawnAmountWriteGuardTest {

    private static final String COLUMN = "withdrawn_amount";
    private static final String TABLE_SQL_MARK = "update settlement";
    private static final int MIN_SCANNED_SOURCE_FILES = 800;

    /**
     * 额外的减法语句白名单：<b>SQL 文本 → 为什么必须有第二条</b>。
     *
     * <p>用 {@code Map} 而不是 {@code Set}（用户点名）：加一条必须写理由，
     * 评审 diff 里能直接看到「这条为什么存在」；{@code Set} 就是个开关，谁都能悄悄加。
     * 当前为空：释放路径只有一处，本来就该唯一。
     */
    private static final java.util.Map<String, String> ALLOWED_EXTRA_DECREMENTS = java.util.Map.of();

    @Test
    @DisplayName("真实扫描 — 写 withdrawn_amount 的 SQL 全部自增自减且带边界；全仓只有一个减点")
    void shouldKeepWithdrawnAmountWritesBoundedAndSingleDecrement() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources();
        assertThat(sources)
                .as("扫描面异常缩小 ⇒ 守卫在空转")
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_SOURCE_FILES);

        List<String> problems = new ArrayList<>();
        int writeLiterals = 0;
        int decrementLiterals = 0;

        for (Path file : sources) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            String name = file.getFileName().toString();
            java.util.Set<String> seenSql = new java.util.HashSet<>();
            for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
                String sql = wholeSql(literal);
                if (!seenSql.add(sql) || !touchesColumn(sql)) {
                    continue;
                }
                if (isWrite(sql)) {
                    writeLiterals++;
                }
                problems.addAll(problemsIn(name, sql));
                if (isDecrement(sql)) {
                    decrementLiterals++;
                }
            }
        }

        assertThat(problems)
                .as("withdrawn_amount 是 E4（列↔明细）的被校验列；写入形状失控则等式必然漂")
                .isEmpty();
        assertThat(writeLiterals)
                .as("已知清单：必须扫到写 withdrawn_amount 的 SQL（扫不到说明字面量被搬到别处了）")
                .isGreaterThanOrEqualTo(1);
        assertThat(decrementLiterals)
                .as("减点必须唯一（释放路径一处）。红了先别放宽本守卫，按顺序做四件事："
                        + "① 问「为什么需要第二条回退路径」—— 是新的业务分支（作废/撤销/部分退回），还是有人又写了一遍释放；"
                        + "② 若是新分支，先在 open-items §1.29 补一条等式（列↔明细、提现↔明细），说明它怎么和 E4/E5 共存；"
                        + "③ 再把该 SQL 按文本加入本守卫的白名单并写清理由（白名单是清单，不是开关）；"
                        + "④ 两条路径都必须带 `WHERE withdrawn_amount >= #{}` 边界 —— 少边界就等于允许减成负数。"
                        + "只把断言从 1 改成 2 而不做 ①②③④，等于把唯一的「多回退路径」探测点关掉。")
                .isEqualTo(1 + ALLOWED_EXTRA_DECREMENTS.size());
    }

    @Test
    @DisplayName("反例1 — 把 withdrawn_amount 写成常数 ⇒ 红")
    void shouldFlagLiteralAssignment() {
        assertThat(problemsIn("X.java", "UPDATE settlement SET withdrawn_amount = 0 WHERE id = ?"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("反例2 — 减法没有边界（可减到负）⇒ 红")
    void shouldFlagUnboundedDecrement() {
        assertThat(problemsIn("X.java",
                "UPDATE settlement SET withdrawn_amount = withdrawn_amount - #{amount} WHERE id = #{id}"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("反例3 — 加法没有「不超过结算额」边界（可超核销）⇒ 红")
    void shouldFlagUnboundedIncrement() {
        assertThat(problemsIn("X.java",
                "UPDATE settlement SET withdrawn_amount = withdrawn_amount + #{amount} WHERE id = #{id}"))
                .isNotEmpty();
    }

    @Test
    @DisplayName("正例 — 真实的加/减两条语句必须放行")
    void shouldPassRealStatements() {
        assertThat(problemsIn("SettlementMapper.java",
                "UPDATE settlement SET withdrawn_amount = withdrawn_amount + #{amount} "
                        + "WHERE id = #{id} AND status = #{settledStatus} "
                        + "AND withdrawn_amount + #{amount} <= settlement_amount"))
                .isEmpty();
        assertThat(problemsIn("SettlementMapper.java",
                "UPDATE settlement SET withdrawn_amount = withdrawn_amount - #{amount} "
                        + "WHERE id = #{id} AND withdrawn_amount >= #{amount}"))
                .isEmpty();
    }

    // ─────────────────────────── 纯判定（可被合成样本直接喂） ───────────────────────────

    /** 单条 SQL 的违规判定（纯函数；真实扫描与反例共用）。 */
    static List<String> problemsIn(String file, String sql) {
        List<String> problems = new ArrayList<>();
        String lower = sql.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains(COLUMN)) {
            return problems;
        }
        // 读不设限：`SELECT settlement_amount - withdrawn_amount AS remaining` 这类是对的，
        // 否则守卫会把「读这一列」也当成写（第一版就是这么误报的）。
        if (lower.trim().startsWith("select")) {
            return problems;
        }
        if (!lower.contains("update settlement") || !lower.contains(" set ")) {
            problems.add(file + ": 出现了 withdrawn_amount，但不是 `UPDATE settlement ... SET` —— "
                    + "写这一列只允许结算单的核销语句（读用 SELECT，不受此限）");
            return problems;
        }
        boolean selfIncrement = lower.contains(COLUMN + " = " + COLUMN + " +");
        boolean selfDecrement = lower.contains(COLUMN + " = " + COLUMN + " -");
        if (!selfIncrement && !selfDecrement) {
            problems.add(file + ": withdrawn_amount 必须写成自增/自减（`= withdrawn_amount ± #{}`），"
                    + "写成常数或直接赋值会绕过「不许超过结算额」");
            return problems;
        }
        if (selfIncrement && !lower.contains(COLUMN + " + #{")) {
            problems.add(file + ": 加法缺参数形式（应为 `withdrawn_amount + #{...}`）");
        }
        if (selfIncrement && !lower.contains("<= " + "settlement_amount")) {
            problems.add(file + ": 加法缺边界 `withdrawn_amount + #{...} <= settlement_amount` ⇒ 可超核销");
        }
        if (selfDecrement && !lower.contains(COLUMN + " >= #{")) {
            problems.add(file + ": 减法缺边界 `withdrawn_amount >= #{...}` ⇒ 可减成负数");
        }
        return problems;
    }

    /** 该 SQL 是否是「减」的那一条（用于全局计数）。 */
    static boolean isDecrement(String sql) {
        String lower = sql.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(COLUMN + " = " + COLUMN + " -");
    }

    /**
     * 把一段「用 + 拼起来的」SQL 还原成一条完整语句。
     *
     * <p>必须还原：JavaParser 把 `"a " + "b "` 看成两个 {@link StringLiteralExpr}，
     * 逐片段判定会把 `AND withdrawn_amount < settlement_amount` 这种尾巴当成独立语句
     * （第一版就是这么误报的）。同一个拼接链只处理一次（调用方按完整文本去重）。
     */
    static String wholeSql(StringLiteralExpr literal) {
        Node node = literal;
        while (node.getParentNode().isPresent()) {
            Node parent = node.getParentNode().get();
            if (parent instanceof BinaryExpr binary && binary.getOperator() == BinaryExpr.Operator.PLUS) {
                node = parent;
            } else {
                break;
            }
        }
        if (node instanceof BinaryExpr binary) {
            StringBuilder joined = new StringBuilder();
            for (StringLiteralExpr piece : binary.findAll(StringLiteralExpr.class)) {
                joined.append(piece.asString());
            }
            return joined.toString();
        }
        return literal.asString();
    }

    /** 是否是对 settlement 写的语句（读语句不算）。 */
    static boolean isWrite(String sql) {
        String lower = sql.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("update settlement") && lower.contains(" set ") && lower.contains(COLUMN);
    }

    /**
     * 是否是「提到该列的 SQL 语句」。
     *
     * <p>必须同时要求 SQL 动词：报错文案里也会出现列名（例如「withdrawn_amount 不足以回退」），
     * 那是给人看的消息，不是语句 —— 守卫不该把它当成写路径（第一版又误报过这里）。
     */
    private static boolean touchesColumn(String sql) {
        String lower = sql.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains(COLUMN)) {
            return false;
        }
        return lower.contains("select ") || lower.contains("update ")
                || lower.contains("insert ") || lower.contains("delete ");
    }
}
