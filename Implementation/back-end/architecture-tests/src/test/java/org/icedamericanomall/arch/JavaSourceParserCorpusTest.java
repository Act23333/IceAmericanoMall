package org.icedamericanomall.arch;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 解析器语料测试 —— 用**真实文件的真实语法形态**把 {@link JavaSourceParser} 钉死。
 *
 * <p><b>这是「换解析器」的验收主体</b>（原 {@code JavaMethodScannerTest} 的 11 条合成用例已随
 * 自写解析器一起删除）。合成样本只能覆盖「我以为会出现的形态」；真实语料才会暴露
 * lambda / 匿名类 / switch / 文本块 / 注解括号 / 泛型 / record 混排的**实际组合**。
 *
 * <p><b>每条断言必须覆盖三件事（缺一即假验收）</b>：
 * <ol>
 *   <li><b>方法数</b> —— 解析出来的方法条数必须等于实测值（少一个就是漏切，多一个就是切错边界）；</li>
 *   <li><b>方法名</b> —— 一组**必然存在**的方法名必须全部命中（局部漏解析的抓法）；</li>
 *   <li><b>body token</b> —— 指定方法体内必须出现指定的调用（<b>只查方法名会被"空壳 body"骗过</b>，
 *       本轮就发生过：方法名对、body 是空的，计数因此恒 0 而判绿）。</li>
 * </ol>
 *
 * <p><b>为什么方法数用「精确相等」而不是「下界」</b>：语料是**冻结样本**，它的价值就在于「一个都不许少」。
 * 若你新增/删除方法导致本用例失败，那是**预期**的 —— 请按实测更新对应常量（这是一份语料，
 * 不是黑盒；更新动作本身就是一次「解析面复核」）。相比之下，门禁里用的是**下界**
 * （{@code FundTransactionGuardTest#MIN_SCANNED_SOURCE_FILES}），因为门禁要跟着仓库长大。
 *
 * <p><b>覆盖文件的选择口径</b>：优先选「资金写 + 复杂语法混排」的类（资金门禁的真实输入面），
 * 外加一个带 {@code @Transactional} 的 Manager（验证注解识别）与一个 MapStruct 之外的转换型仓储
 * （验证大 body 不被截断）。
 */
@DisplayName("解析器语料 — 方法数 + 方法名 + body token（真实文件，三件事齐备）")
class JavaSourceParserCorpusTest {

    /** 语料声明：文件路径（相对 back-end 根）、期望方法数、必须存在的方法名、必须出现的调用。 */
    record Corpus(String path, int methodCount, List<String> mustExist,
                  Map<String, List<String>> bodyTokens) { }

    private static Corpus corpus(String path, int count, List<String> mustExist,
                                 Map<String, List<String>> tokens) {
        return new Corpus(path, count, mustExist, tokens);
    }

    private static final List<Corpus> CORPORA = List.of(
            corpus("wallet-service/src/main/java/org/icedamericanomall/wallet/app/WalletManager.java",
                    4,
                    List.of("getBalance", "deductBalance", "recharge", "refund"),
                    Map.of(
                            // 资金写 + 幂等查重：旧解析器在 refund 上根本切不出方法体
                            "refund", List.of("existsByTransactionNo", "credit", "insert"),
                            "deductBalance", List.of("insert", "deduct"))),
            corpus("pay-service/src/main/java/org/icedamericanomall/pay/app/PayManager.java",
                    7,
                    List.of("initiatePayment", "initiateJsapiPayment", "resolvePayAmount",
                            "reuseOrCreatePending", "payByBalance", "queryStatus", "cancelTimeoutPayOrders"),
                    Map.of(
                            // 假阴性现场：3 个本地资金写 + 中间一次 Feign，旧解析器数成 0
                            "payByBalance", List.of("createPending", "deductBalance",
                                    "markBalancePaidIfPending"),
                            "cancelTimeoutPayOrders", List.of("listTimeout", "cancelPending",
                                    "notifyCancelled"))),
            corpus("pay-service/src/main/java/org/icedamericanomall/pay/app/RefundManager.java",
                    12,
                    List.of("refund", "doRefund", "retry", "doRetry", "execute", "dispatchAndSettle",
                            "requestChannel", "markSuccessAndNotify", "markFailedWithReason",
                            "validate", "requirePayOrder", "abbreviate"),
                    Map.of(
                            "requestChannel", List.of("refund", "settled"),
                            "markSuccessAndNotify", List.of("markSuccessIfProcessing", "onRefundSuccess"))),
            corpus("pay-service/src/main/java/org/icedamericanomall/pay/app/ReconciliationManager.java",
                    8,
                    List.of("run", "reconcile", "saveFailedBatch", "saveSucceededBatch", "download",
                            "toBillEntries", "pageBatches", "pageDetails"),
                    Map.of(
                            // 互斥分支已拆方法（D 批）：每个方法各只有一个资金写
                            "reconcile", List.of("download", "saveFailedBatch", "saveSucceededBatch"),
                            "saveSucceededBatch", List.of("listPaidByDateAndChannel", "compare",
                                    "saveBatchResult"))),
            corpus("trade-service/src/main/java/org/icedamericanomall/trade/app/order/OrderLifecycleManager.java",
                    12,
                    List.of("cancelOrder", "confirmReceipt", "shipOrder", "cancelOrderSafely",
                            "restoreStock", "rollbackCoupon", "awardOrderRewardPoints"),
                    Map.of(
                            // 含 lambda（stream/forEach）与 Feign：切错边界会漏掉后半段
                            "shipOrder", List.of("shipPackage", "createLogistics", "publishOrderShipped"),
                            "restoreStock", List.of("findItemsByOrderId", "restoreStock"))),
            corpus("trade-service/src/main/java/org/icedamericanomall/trade/app/invoice/InvoiceBuyerManager.java",
                    10,
                    List.of("createTitle", "updateTitle", "deleteTitle", "pageMy", "listTitles"),
                    Map.of(
                            // 注解 + 事务边界：@Transactional 必须能被读到（决定该不该判红）
                            "createTitle", List.of("clearDefaultFlag", "save"),
                            "updateTitle", List.of("clearDefaultFlag", "updateFull"))),
            corpus("trade-service/src/main/java/org/icedamericanomall/trade/infrastructure/persistence/invoice/"
                            + "InvoiceTitleRepositoryImpl.java",
                    11,
                    List.of("findById", "save", "updateFull", "clearDefaultFlag", "toDomain", "toPO"),
                    Map.of(
                            // po ↔ 聚合转换：几十行 setter/getter 连排，旧实现的「截断」在这里最明显
                            "toDomain", List.of("setId", "getTaxNoCipher"),
                            "toPO", List.of("setTaxNoHash", "getMask"))));

    /**
     * 压力样本：全仓**语法最复杂**的真实文件。
     *
     * <p>怎么选出来的（实测，不是感觉）：解析全部主源码后按**语句数**降序取第一 ——
     * `OrderRepositoryImpl` 语句数最多、方法数最多、单方法体最大，是「解析器最可能切错」的那一份
     * （同时它也是全仓最大的类，见 `class-size-baseline.tsv` 的登记）。
     * lambda 密度第一的是 `KgEdgeIndexAdapter`（几十处 lambda），若将来要再加一份压力样本，按它选。
     *
     * <p><b>本用例不做业务判定、不为抓违规</b>：它只锁「JavaParser 在这种体量/形态上的行为」——
     * 把它换成任何别的解析器或降低容错，这里必须先红。
     * 断言口径与其余语料一致：**能精确的精确（方法与类型名），会自然增长的取下界（语句数 / body 长度）**，
     * 免得每加一行代码就要改测试（那种测试最后一定会被人用 `@Disabled` 关掉）。
     *
     * <p><b>维护成本与换样条件（写死，别靠临场判断）</b>：精确方法数会随该类改动而红，所以它只适合**不常改**的文件。
     * 判据：**`OrderRepositoryImpl` 的变更频率一旦超过「每月一次」，就把它换成 `KgEdgeIndexAdapter`**
     * （lambda 密度第一、结构稳定）。查频率的命令（近 30 天该文件的提交数）：
     * <pre>
     *   git log --since="1 month ago" --oneline -- \
     *     Implementation/back-end/trade-service/src/main/java/org/icedamericanomall/trade/infrastructure/persistence/order/OrderRepositoryImpl.java | wc -l
     * </pre>
     * 结果 &gt; 1 ⇒ 换样本；换时把本段与 `STRESS_*` 常量一起改，不要只改常量。
     * 理由：冻结样本的价值在「一个都不许少」，一旦它每月都要改，就会被人顺手改成 `@Disabled` 或放宽成下界 ——
     * 那时它既不再冻结，也不再有告警能力。
     */
    private static final String STRESS_FILE =
            "trade-service/src/main/java/org/icedamericanomall/trade/infrastructure/persistence/order/"
                    + "OrderRepositoryImpl.java";

    /** 精确值（方法数只在「真的增删方法」时变；失败信息里带维护提示）。 */
    private static final int STRESS_METHOD_COUNT = 35;

    /** 下界（只抓「解析器丢了子树」；代码自然增长不会红）。 */
    private static final int STRESS_MIN_STATEMENTS = 300;

    /** 下界：最大单方法体的字符数（截断它 = 解析器在长方法上出问题）。 */
    private static final int STRESS_MIN_LARGEST_BODY = 2000;

    @Test
    @DisplayName("★ 七个真实文件 — 方法数 / 方法名 / body token 三项全中")
    void shouldParseRealCorpora() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();

        for (Corpus entry : CORPORA) {
            Path file = root.resolve(entry.path());
            assertThat(file)
                    .as("语料文件不存在（路径漂移？）：" + entry.path())
                    .exists();

            List<MethodDeclaration> methods = JavaSourceParser.methods(file);
            List<String> names = methods.stream().map(MethodDeclaration::getNameAsString).toList();

            // ① 方法数（精确：语料是冻结样本）
            if (methods.size() != entry.methodCount()) {
                problems.add(entry.path() + " —— 方法数 " + methods.size()
                        + "，期望 " + entry.methodCount() + "；实测方法名 " + names
                        + "\n         ↳ 维护提示：**本断言失败不一定代表解析器坏了**。若你刚给该类"
                        + "新增/删除/改名了方法，那是**预期**的 —— 请把常量改成实测值"
                        + "（即把这条失败信息里的「方法数」抄进 `corpus(...)` 的第二个参数），"
                        + "并在同一个提交里确认「方法名」清单仍然齐全。"
                        + "\n         ↳ 只有在**没动过该类**时失败，才应怀疑解析器漏切（那才是真红）。");
            }

            // ② 方法名（必然存在的一组）
            for (String expected : entry.mustExist()) {
                if (!names.contains(expected)) {
                    problems.add(entry.path() + " —— 缺少方法 " + expected);
                }
            }

            // ③ body token（按调用名判定，不受书写格式影响；区分「空壳 body」与「真解析」）
            Map<String, MethodDeclaration> byName = new LinkedHashMap<>();
            methods.forEach(m -> byName.putIfAbsent(m.getNameAsString(), m));
            entry.bodyTokens().forEach((methodName, tokens) -> {
                MethodDeclaration method = byName.get(methodName);
                if (method == null) {
                    problems.add(entry.path() + "#" + methodName + " —— 方法未被解析出来");
                    return;
                }
                String bodyText = JavaSourceParser.body(method);
                if (bodyText.isBlank()) {
                    problems.add(entry.path() + "#" + methodName + " —— body 为空（空壳）");
                    return;
                }
                // ★ 断言落在 **body 文本**上（而不是只在 AST 调用列表里）：
                //   body 文本是「这个方法到底切出了多少」的直接观测面。只查 AST 的话，
                //   即使 body() 被截断/返回空壳，断言照样绿 —— M17 变异实测过这个洞。
                for (String token : tokens) {
                    if (!bodyText.contains(token)) {
                        problems.add(entry.path() + "#" + methodName + " —— body 缺 token " + token
                                + "（body 长度 " + bodyText.length() + "，疑似截断）");
                    }
                }
            });
        }

        assertThat(problems)
                .as("解析器在真实语料上不可信 ⇒ 依赖它的门禁（资金写事务）的绿也是假的")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 反空前提 — 语料里每个具体方法都必须有 body（空壳=假绿）")
    void shouldNeverReturnEmptyBodyForConcreteClasses() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> shellMethods = new ArrayList<>();
        int inspected = 0;

        for (Corpus entry : CORPORA) {
            Path file = root.resolve(entry.path());
            var unit = JavaSourceParser.parse(file);
            assertThat(JavaSourceParser.isInterfaceFile(unit))
                    .as("语料选的是具体类，不该是接口：" + entry.path())
                    .isFalse();
            for (MethodDeclaration method : JavaSourceParser.methods(unit)) {
                inspected++;
                if (JavaSourceParser.hasNoBody(method) || JavaSourceParser.body(method).isBlank()) {
                    shellMethods.add(entry.path() + "#" + method.getNameAsString());
                }
            }
        }

        // 反空断言落在**扫描面**上（而不是"违规集合为空"）：一片真空也会让上面那条通过
        assertThat(inspected)
                .as("一个方法都没检查到 ⇒ 语料断言在空转")
                .isGreaterThan(50);
        assertThat(shellMethods)
                .as("具体类的方法必须都有 body；为空说明解析器给的是空壳节点")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 解析失败必须抛（禁止退化为空 AST —— 假绿的源头）")
    void shouldThrow_whenSourceIsNotParsable() {
        assertThatThrownBy(() -> JavaSourceParser.parse("class Broken { void m( { }", "broken.java"))
                .as("解析失败若返回空集合，门禁会「扫到 0 个方法」并因此判绿")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JavaParser 解析失败")
                .hasMessageContaining("broken.java");
    }

    @Test
    @DisplayName("★ 注解可读 — @Transactional 能从 AST 读到（不再靠正则反抽）")
    void shouldReadAnnotations() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path wallet = root.resolve(
                "wallet-service/src/main/java/org/icedamericanomall/wallet/app/WalletManager.java");

        Map<String, Boolean> transactional = new LinkedHashMap<>();
        for (MethodDeclaration method : JavaSourceParser.methods(wallet)) {
            transactional.put(method.getNameAsString(),
                    JavaSourceParser.hasAnnotation(method, "Transactional"));
        }

        assertThat(transactional)
                .as("deductBalance / refund 带 @Transactional（修复假流水的那个注解）")
                .containsEntry("deductBalance", true)
                .containsEntry("refund", true)
                .containsEntry("getBalance", false);
    }

    @Test
    @DisplayName("★ 扫描面 — mainSources() 必须非空且不含 architecture-tests 自身")
    void shouldScanMainSourcesOnly() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources();

        assertThat(sources)
                .as("路径未归一化时这里会是 0（本轮真实缺陷形态）")
                .isNotEmpty();
        assertThat(sources)
                .as("architecture-tests 自己的测试源码不得进入业务扫描面")
                .noneMatch(p -> p.toString().contains("architecture-tests"));
        assertThat(sources)
                .as("必须全部落在 src/main/java 下")
                .allMatch(p -> p.toString().replace('\\', '/').contains("/src/main/java/"));
    }

    @Test
    @DisplayName("★ 压力样本 — 全仓最复杂文件：结构与体量不许随解析器变化")
    void shouldLockParserBehaviour_onMostComplexFile() throws IOException {
        Path file = Path.of("..").toAbsolutePath().normalize().resolve(STRESS_FILE);
        assertThat(file).as("压力样本文件不存在（路径漂移？）：" + STRESS_FILE).exists();

        var unit = JavaSourceParser.parse(file);
        List<MethodDeclaration> methods = JavaSourceParser.methods(unit);
        List<String> names = methods.stream().map(MethodDeclaration::getNameAsString).toList();

        // ① 类型名：整份文件只有一个顶层类，且名字没被解析搅乱
        assertThat(JavaSourceParser.typeNames(unit))
                .as("顶层类型解析结果")
                .containsExactly("OrderRepositoryImpl");

        // ② 方法数（精确 + 维护提示）
        assertThat(methods)
                .as("方法数 %s，期望 %s；实测方法名 %s"
                                + "\n   ↳ 维护提示：**若你刚增删/改名了方法，这是预期的** —— 请把实测值抄进"
                                + " STRESS_METHOD_COUNT，并在同一提交里复核本用例其余断言；"
                                + "\n     只有**没动过该类**时失败，才应怀疑解析器漏切。",
                        methods.size(), STRESS_METHOD_COUNT, names)
                .hasSize(STRESS_METHOD_COUNT);

        // ③ 每个方法都必须有非空 body 与名字（空壳/无名方法 = 解析器给了半个节点）
        List<String> shells = new ArrayList<>();
        for (MethodDeclaration method : methods) {
            if (method.getNameAsString().isBlank()
                    || JavaSourceParser.hasNoBody(method)
                    || JavaSourceParser.body(method).isBlank()) {
                shells.add(String.valueOf(method.getNameAsString()));
            }
        }
        assertThat(shells).as("具体类的方法必须有名字且有 body").isEmpty();

        // ④ 子树不许丢：语句数是「嵌套块/匿名类/lambda 有没有被完整遍历」的直接观测面
        int statements = unit.findAll(Statement.class).size();
        assertThat(statements)
                .as("语句数 %s 低于下界 %s ⇒ 解析器在这份文件上丢了语法子树（合法增长不会触发）",
                        statements, STRESS_MIN_STATEMENTS)
                .isGreaterThanOrEqualTo(STRESS_MIN_STATEMENTS);

        // ⑤ 单方法体不许被截断：取最大 body 的字符数
        int largest = methods.stream().mapToInt(m -> JavaSourceParser.body(m).length()).max().orElse(0);
        assertThat(largest)
                .as("最大单方法体只有 %s 字符（下界 %s）⇒ 长方法被截断", largest, STRESS_MIN_LARGEST_BODY)
                .isGreaterThanOrEqualTo(STRESS_MIN_LARGEST_BODY);

        // ⑥ body token：抽两个跨越不同分支的方法，确认切出的 body 是「那一段」而不是别的段
        Map<String, MethodDeclaration> byName = new LinkedHashMap<>();
        methods.forEach(m -> byName.putIfAbsent(m.getNameAsString(), m));
        assertThat(JavaSourceParser.body(byName.get("saveWithItems")))
                .as("saveWithItems 的 body 应含 insert（落库）与 toPO（转换）")
                .contains("insert")
                .contains("toPO");
        assertThat(JavaSourceParser.body(byName.get("shipPackage")))
                .as("shipPackage 的 body 应含条件更新 markPackageShipped（ADR-037 的契约化 SQL）")
                .contains("markPackageShipped");
    }

    // ═══════════════ receiver 形态边界（登记 + 覆盖，不靠注释） ═══════════════

    /** 把一段方法体包成可解析的类，取方法 m（用文本块避免转义，源码里的换行就是文本块里的换行）。 */
    private static MethodDeclaration methodOf(String body) {
        String src = """
                class S {
                    Object walletAccountRepository;
                    Object walletTransactionRepository;
                    Object getWalletAccountRepository() { return null; }
                    void m() %s
                }
                """.formatted(body);
        return JavaSourceParser.methods(JavaSourceParser.parse(src)).stream()
                .filter(m -> m.getNameAsString().equals("m"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("样本方法 m 未被解析出来（解析器退化）"));
    }

    private static int fundWritesIn(String body) {
        return FundTransactionGuardTest.countFundWrites(methodOf(body));
    }

    /**
     * <b>这条用例锁的是「实现不许回退」，不是「仓库里现在有没有这种写法」。</b>
     *
     * <p>⚠️ **不要因为它"全仓零命中"就把本用例删掉当冗余** —— 它防的正是这种删除：
     * 一旦有人把 {@code JavaSourceParser#receiverName} 里的 {@code FieldAccessExpr(scope=this)}
     * 分支去掉（或把 receiver 判定退回「只认 {@code NameExpr}」），`this.repo.write(...)` 就会
     * **静默漏算**（同一句资金写换个前缀就不计数）。那时本用例立刻红 —— 这是唯一的告警。
     *
     * <p>判据上的区别：语料测试里的「零命中体检」（{@code shouldHaveNoFactoryChainedFundWrites}）
     * 抓的是"真实代码里出现了漏算写法"；本用例抓的是"漏算能力被加回来了"。**两者缺一不可**。
     */
    @Test
    @DisplayName("★ 边界 — this.<repo>.write(...) 必须计数（锁实现不被回退，勿因零命中而删）")
    void shouldCountThisPrefixedFundWrite() {
        assertThat(fundWritesIn("{ this.walletAccountRepository.deduct(userId, amount); }"))
                .as("this. 前缀只是接收者的另一种写法，语义上就是同一句资金写 —— 漏算即假阴性")
                .isEqualTo(1);
        assertThat(fundWritesIn("{ this.walletTransactionRepository.insert(tx); }"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("★ 边界 — 工厂/链式 receiver 不计（已登记边界，行为钉死不许悄悄变）")
    void shouldIgnoreFactoryChainedCall() {
        assertThat(fundWritesIn("{ getWalletAccountRepository().credit(userId, amount); }"))
                .as("receiver 是表达式而非名字 ⇒ 名字匹配判不了；这是**登记**的边界（见 receiverName 注释）。"
                        + "若将来补了类型解析并开始计数，本断言会红 —— 那时是**故意**改成 1，不要顺手放宽判据。")
                .isZero();
    }

    @Test
    @DisplayName("★ 体检 — 全仓不得出现「工厂/链式」资金写（出现即说明边界被踩到）")
    void shouldHaveNoFactoryChainedFundWrites() throws IOException {
        record Hit(String file, int line, String text) { }
        List<Hit> hits = new ArrayList<>();
        int scannedCalls = 0;
        String fundRepo = "(?i).*(Account|Transaction|PayOrder|RefundOrder|Reconciliation|Settle"
                + "|Settlement|Ledger|Points|Freeze|Frozen|Flow).*(Repository|Mapper|Service|Client"
                + "|Gateway|Port)";
        String writeVerb = "(?i)(insert|save|update|delete|deduct|credit|mark|create|cancel|void|bind"
                + "|freeze|unfreeze).*";

        for (Path file : JavaSourceParser.mainSources()) {
            for (var call : JavaSourceParser.parse(file)
                    .findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
                scannedCalls++;
                var scope = call.getScope();
                if (scope.isEmpty() || JavaSourceParser.receiverName(scope.get()).isPresent()) {
                    continue;
                }
                // 只看「receiver 还原不出名字」且内层调用名像资金仓储、外层是写动词的形态：
                // 这是唯一会被漏算且**看起来像**资金写的一类，正是本体检要抓的。
                if (scope.get() instanceof com.github.javaparser.ast.expr.MethodCallExpr inner
                        && inner.getNameAsString().matches(fundRepo)
                        && call.getNameAsString().matches(writeVerb)) {
                    int line = call.getBegin().map(pos -> pos.line).orElse(0);
                    hits.add(new Hit(file.getFileName().toString(), line, call.toString()));
                }
            }
        }

        // 反空断言落在**扫描面**上：一个调用都没扫到 = 体检在空转
        assertThat(scannedCalls)
                .as("扫描面异常：调用表达式数量过少，体检在空转")
                .isGreaterThan(10000);
        assertThat(hits)
                .as("发现「工厂/链式」形态的资金写（门禁会漏算它）：%s；处理二选一："
                        + "① 改成字段名写法（repo.write(...) / this.repo.write(...)）；"
                        + "② 给 countFundWrites 补类型解析（届时要同步改 shouldIgnoreFactoryChainedCall）。", hits)
                .isEmpty();
    }
}
