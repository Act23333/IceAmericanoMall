package org.icedamericanomall.arch;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退役状态守卫 —— {@code SettlementStatusEnum.PAID_OUT} 是**死枚举值**（保留，但不得再被写进结算单）。
 *
 * <p><b>为什么保留而不是删</b>：值已存在于列注释、历史数据与文档里；删它会让「历史上确实有过这个状态」
 * 这件事失去唯一可引用的符号，下一次有人写数据迁移/读模型时会自己造一个魔数 {@code 3}。
 * 所以策略是**保留符号、封死写路径**。
 *
 * <p><b>口径（用户裁决，写死）</b>：`settlement.status` 不承载打款事实 —— {@code SETTLED} = 「可提现」，
 * 「已打款」的唯一事实源是 {@code withdrawal.status = PAID_OUT}。因此本守卫封的是**写**，不是**读**：
 * <ul>
 *   <li><b>禁止</b>：{@code SettlementStatusEnum.PAID_OUT} 作为 {@code setStatus} / {@code update*} /
 *       {@code settle*} / {@code mark*} / {@code review*} 的实参（含 {@code .getCode()} 包装）；</li>
 *   <li><b>禁止（变量绕过）</b>：把它**存进变量** —— 赋值右侧（`code = …`）或变量初始化（`int code = …`）。
 *       这是「不当实参直接传」的经典绕过形态：存下来之后静态就看不住怎么用了，
 *       所以规则必须在**存的那一刻**红，而不是等到它被当成 toStatus 传进去；</li>
 *   <li><b>禁止</b>：任何 {@code UPDATE settlement} 的 SQL 把 {@code status} 写成**数字字面量**
 *       （目标状态必须走 {@code #{toStatus}} 参数，而参数来源受上面两条约束）；</li>
 *   <li><b>禁止</b>：静态导入该常量（会让「显式 scope」判定失效，等于把守卫弄瞎）；</li>
 *   <li><b>允许</b>：读侧比较/switch（将来做「结算单列表区分已打款」的读模型时可以用它判断历史行）。</li>
 * </ul>
 *
 * <p><b>静态扫描的固有局限（登记，不假装守住）</b>：
 * <ol>
 *   <li>SQL 只检查**单个字符串字面量**内的写法。若把 {@code "UPDATE settlement SET status = "} 与
 *       {@code "3"} 拼在两个字面量里，本守卫看不见；</li>
 *   <li>**容器/反射/字符串化**绕过看不见：{@code List.of(SettlementStatusEnum.PAID_OUT)}、
 *       {@code map.put(k, …)}、{@code SettlementStatusEnum.valueOf("PAID_OUT")} 都不会被上面的规则命中
 *       （{@code name()} 反射取常量同理）；</li>
 *   <li>这些属「刻意绕过」，与 {@code FundTransactionGuardTest} 的两跳局限同族 —— **靠 review，不靠门禁**；
 *       条数一旦变多，说明该改设计（例如干脆删掉枚举值），而不是继续加正则。</li>
 * </ol>
 *
 * <p><b>扫描面断言（判据 #16 三形态）</b>：① 源文件数下界；② 已知清单 —— 必须扫到
 * {@code SettlementMapper} 里那句**合法**的 {@code UPDATE settlement ... status = #{toStatus}}，
 * 以及至少一处对 {@code SettlementStatusEnum.SETTLED} 的引用（证明「枚举引用」这条扫描面确实在工作，
 * 否则零引用可能只是扫描器瞎了）；③ 反例 —— 五条合成样本（写实参 / 字面量 SQL / **变量绕过** / 静态导入 / 合法样本）。
 *
 * <p><b>退出条件（写死，别让它变成第 N 道判据）</b>：后续步骤动手时先确认**读模型到底需不需要
 * {@code PAID_OUT}**（按本批语义答案是「不需要」—— 打款去 {@code withdrawal} 查）。
 * 若确认不需要：<b>删枚举值 + 删本类</b>，并同步清理三处引用
 * （{@code Initialize.sql} 的列注释与表注释、{@code 04-Data-Model} §7.4、交接单里提到 {@code status = 3} 的地方）。
 * <b>不要</b>在这种情况下再加第五条判据 —— 死符号越守越像真符号，**删干净比守住便宜**；
 * 只有当读模型确实需要它（历史数据必须有可引用的符号）时，才保留符号并让本守卫继续存在。
 */
@DisplayName("退役状态守卫 — SettlementStatusEnum.PAID_OUT 不得写进结算单")
class SettlementPaidOutRetiredGuardTest {

    /** 已退役的常量名（保留符号，只封写路径）。 */
    private static final String RETIRED = "PAID_OUT";

    /** 其枚举宿主（显式 scope 才认，避免与 WithdrawalStatusEnum.PAID_OUT 混淆）。 */
    private static final String HOST = "SettlementStatusEnum";

    /** 源文件数下界（与 FundTransactionGuardTest 同口径：抓「整体扫不到」，不抓单文件漏扫）。 */
    private static final int MIN_SCANNED_SOURCE_FILES = 800;

    /** {@code status = 3} 这类字面量赋值（只用于检查 {@code UPDATE settlement} 语句内部）。 */
    private static final Pattern STATUS_LITERAL = Pattern.compile("status\\s*=\\s*(\\d+)");

    /** 视为「写」的方法名（小写前缀匹配）；{@code setStatus} 单独列入以防漏。 */
    private static final List<String> WRITE_CALL_PREFIXES =
            List.of("setstatus", "update", "settle", "mark", "review");

    /** 取值包装（继续上溯找写调用）；遇到其它调用即视为「读」并停止。 */
    private static final List<String> VALUE_WRAPPERS = List.of("getcode", "of", "valueof");

    // ─────────────────────────── 主用例 ───────────────────────────

    @Test
    @DisplayName("真实扫描 — 无写路径引用 PAID_OUT；无字面量写 settlement.status；扫描面三形态齐备")
    void shouldNotWriteRetiredPaidOutIntoSettlement() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources();
        assertThat(sources)
                .as("扫描面异常缩小：源文件数 < " + MIN_SCANNED_SOURCE_FILES + " ⇒ 守卫在空转")
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_SOURCE_FILES);

        List<String> problems = new ArrayList<>();
        int sqlLiteralsSeen = 0;
        int settledRefsSeen = 0;

        for (Path file : sources) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            String name = file.getFileName().toString();
            problems.addAll(problemsIn(name, unit));
            for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
                if (containsUpdateSettlement(literal.asString())) {
                    sqlLiteralsSeen++;
                }
            }
            settledRefsSeen += countSettledReferences(unit);
        }

        assertThat(problems)
                .as("已打款事实源是 withdrawal；把 PAID_OUT 写回结算单会让「结算单 = 已打款」这个错误口径复活")
                .isEmpty();

        // 已知清单①：合法的参数化 SQL 必须被扫到（否则「零字面量」只是「没看见 SQL」）
        assertThat(sqlLiteralsSeen)
                .as("扫描面失效：一句 `UPDATE settlement` 都没扫到（SettlementMapper#settleIfPending 必然有一句）")
                .isGreaterThanOrEqualTo(1);
        // 已知清单②：枚举引用必须可被识别（否则「零写引用」可能只是扫描器看不到 FieldAccessExpr）
        assertThat(settledRefsSeen)
                .as("扫描面失效：一处 `SettlementStatusEnum.SETTLED` 都没数到，说明枚举引用判定本身坏了")
                .isGreaterThanOrEqualTo(1);
    }

    // ─────────────────────────── 反例（合成输入） ───────────────────────────

    @Test
    @DisplayName("★ 反例1 — 把 PAID_OUT 当 setStatus 实参（含 .getCode() 包装）→ 红")
    void shouldFlag_whenRetiredUsedAsWriteArgument() {
        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { void m(org.icedamericanomall.trade.domain.model.finance.Settlement s) {"
                        + " s.setStatus(SettlementStatusEnum.PAID_OUT.getCode()); } }")))
                .as("写路径实参（哪怕套了一层 getCode）必须被认出来")
                .hasSize(1);

        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { void m() { settlementRepository.updateStatus(SettlementStatusEnum.PAID_OUT); } }")))
                .as("直接作为 `update*` 的实参同样必须红")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 反例2 — `UPDATE settlement` 把 status 写成字面量 3 → 红")
    void shouldFlag_whenSqlWritesLiteralStatus() {
        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { String sql() { return \"UPDATE settlement SET status = 3 WHERE id = ?\"; } }")))
                .as("字面量 3 是 PAID_OUT 的码值；写死它等于绕过枚举、也绕过本守卫")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 反例3 — 变量绕过：把 PAID_OUT 存进变量（初始化 / 赋值）→ 红")
    void shouldFlag_whenRetiredStoredIntoVariable() {
        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { void m() { int code = SettlementStatusEnum.PAID_OUT.getCode(); } }")))
                .as("变量初始化：存下来之后静态看不住怎么用，必须在存的那一刻红")
                .hasSize(1);

        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { void m() { int code = 0; code = SettlementStatusEnum.PAID_OUT.getCode(); } }")))
                .as("赋值右侧同样要红（否则 `int code = 3; code = PAID_OUT.getCode();` 就能绕过）")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 反例4 — 静态导入 PAID_OUT → 红（它会让 scope 判定失效）")
    void shouldFlag_whenRetiredIsStaticallyImported() {
        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "import static org.icedamericanomall.trade.domain.enums.SettlementStatusEnum.PAID_OUT;"
                        + "class S { }")))
                .as("静态导入之后所有引用都变成裸 PAID_OUT，本守卫的「显式 scope」判定就瞎了")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 正例 — 参数化 SQL + 只引用 SETTLED（读侧用 PAID_OUT 也放行）→ 绿")
    void shouldPass_whenParameterizedAndReadOnly() {
        assertThat(problemsIn("Sample.java", JavaSourceParser.parse(
                "class S { String sql() { return \"UPDATE settlement SET status = #{toStatus} "
                        + "WHERE id = #{id} AND status = #{fromStatus}\"; } }"
                        + "class R { boolean m(int code) { return code == SettlementStatusEnum.PAID_OUT.getCode(); } }")))
                .as("参数化的目标状态 + 只读比较都合法：守卫封的是**写**，不是**读**（将来读模型要用它判历史行）")
                .isEmpty();
    }

    // ─────────────────────────── 纯函数（可被合成样本直接喂） ───────────────────────────

    /** 单文件的违规判定（纯函数；真实扫描与反例共用同一份判据）。 */
    static List<String> problemsIn(String file, CompilationUnit unit) {
        List<String> problems = new ArrayList<>();

        for (ImportDeclaration imp : unit.findAll(ImportDeclaration.class)) {
            if (imp.isStatic() && imp.getNameAsString().endsWith(HOST + "." + RETIRED)) {
                problems.add(file + ": 静态导入了退役常量 " + RETIRED
                        + " —— 会让「显式 scope」判定失效，请改成显式 " + HOST + "." + RETIRED + "（且只能读）");
            }
        }

        for (FieldAccessExpr ref : unit.findAll(FieldAccessExpr.class)) {
            if (!RETIRED.equals(ref.getNameAsString())) {
                continue;
            }
            if (!(ref.getScope() instanceof NameExpr scope) || !HOST.equals(scope.getNameAsString())) {
                continue;   // 不是本枚举的常量（例如 WithdrawalStatusEnum.PAID_OUT），与退役无关
            }
            String writeCall = enclosingWriteCall(ref);
            if (writeCall != null) {
                problems.add(file + ": 把已退役的 " + HOST + "." + RETIRED + " 当作 `" + writeCall
                        + "(…)` 的实参 —— 已打款事实源是 withdrawal，结算单不得承载该状态");
                continue;   // 同一处只报一次
            }
            String assignment = assignmentBypass(ref);
            if (assignment != null) {
                problems.add(file + ": 把已退役的 " + HOST + "." + RETIRED + " 存进变量（" + assignment
                        + "）—— 变量绕过是「不当实参、先存下来」的经典形态，必须在存的那一刻红");
            }
        }

        for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
            String sql = literal.asString();
            if (!containsUpdateSettlement(sql)) {
                continue;
            }
            Matcher m = STATUS_LITERAL.matcher(sql.toLowerCase(Locale.ROOT));
            while (m.find()) {
                problems.add(file + ": `UPDATE settlement` 把 status 写成字面量 " + m.group(1)
                        + " —— 目标状态必须走 #{toStatus} 参数，且只允许 PENDING/SETTLED");
            }
        }
        return problems;
    }

    /** 该引用是否被「存进变量」（变量初始化 / 赋值右侧）；返回描述或 null。 */
    private static String assignmentBypass(FieldAccessExpr ref) {
        Node child = ref;
        Optional<Node> cursor = ref.getParentNode();
        while (cursor.isPresent()) {
            Node parent = cursor.get();
            if (parent instanceof AssignExpr assign && assign.getValue() == child) {
                return "赋值右侧 `= …`";
            }
            if (parent instanceof VariableDeclarator declarator) {
                // 不用 lambda 捕获 child：child 在循环里被重新赋值，不是 effectively final（编译期会直接拒）
                Optional<Expression> initializer = declarator.getInitializer();
                if (initializer.isPresent() && initializer.get() == child) {
                    return "变量初始化";
                }
            }
            if (parent instanceof MethodCallExpr call) {
                String lower = call.getNameAsString().toLowerCase(Locale.ROOT);
                if (VALUE_WRAPPERS.stream().noneMatch(lower::startsWith)) {
                    return null;    // 比较/组装等读上下文，放行
                }
            }
            child = parent;
            cursor = parent.getParentNode();
        }
        return null;
    }

    /** 该引用是否落在「写类调用」的实参上（沿父链上溯，穿过 getCode() 之类的取值包装）。 */
    private static String enclosingWriteCall(FieldAccessExpr ref) {
        Optional<Node> cursor = ref.getParentNode();
        while (cursor.isPresent()) {
            Node parent = cursor.get();
            if (parent instanceof MethodCallExpr call) {
                String name = call.getNameAsString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (WRITE_CALL_PREFIXES.stream().anyMatch(lower::startsWith)) {
                    return name;
                }
                if (VALUE_WRAPPERS.stream().noneMatch(lower::startsWith)) {
                    return null;    // 遇到无关调用（比较/组装）⇒ 视为「读」，放行
                }
            }
            cursor = parent.getParentNode();
        }
        return null;
    }

    private static boolean containsUpdateSettlement(String sql) {
        return sql.toLowerCase(Locale.ROOT).contains("update settlement");
    }

    /** 数 {@code SettlementStatusEnum.SETTLED} 的引用（扫描面断言用）。 */
    private static int countSettledReferences(CompilationUnit unit) {
        int n = 0;
        for (FieldAccessExpr ref : unit.findAll(FieldAccessExpr.class)) {
            if ("SETTLED".equals(ref.getNameAsString())
                    && ref.getScope() instanceof NameExpr scope
                    && HOST.equals(scope.getNameAsString())) {
                n++;
            }
        }
        return n;
    }
}
