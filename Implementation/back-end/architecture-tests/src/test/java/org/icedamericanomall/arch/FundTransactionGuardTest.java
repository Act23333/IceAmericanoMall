package org.icedamericanomall.arch;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金类多写操作必须带 {@code @Transactional} 的门禁。
 *
 * <p><b>判定口径（同一方法内）</b>：≥2 个资金写 + 无 {@code @Transactional} ⇒ 红；
 * 含 Feign 的多写同样红（Feign 不能进事务 ⇒ 无法用事务保护 ⇒ 必须登记豁免理由）。
 *
 * <p><b>解析换了工具（A1，2026-10-19）</b>：方法切分与注解识别一律走
 * {@link JavaSourceParser}（JavaParser AST），**不再**用「正则 + 花括号深度」自写的
 * {@code MethodScanner}（已删除，连同其 11 条合成用例）。旧实现的失效形态是
 * <b>方法名正确、body 被截断</b>：同一个方法在完整 body 上计数是 3、在截断 body 上是 0，
 * 于是「安全地」判了绿 —— 这不是判据错，是**输入错**。
 *
 * <p>顺带被删掉的还有两处<b>规避性代码</b>（换工具后不再需要）：
 * <ul>
 *   <li>「用正则按 {@code @Transactional ... 方法名(} 反抽事务方法名」—— AST 直接读注解，
 *       正则版会把注解参数里的圆括号算进去；</li>
 *   <li>「先 {@code stripComments} 再扫」—— AST 里注释根本不在语法树上，天然免疫
 *       「注释里写着 @Transactional 就被当成有注解」这一类假阳性。</li>
 * </ul>
 *
 * <p><b>扫描面断言（判据 #16 三形态齐备）</b>：
 * ① <b>数量</b> —— 源码文件数与「通过预过滤的文件数」双重下界（扫不到就红，不许空集通过）；
 * ② <b>已知清单</b> —— 已知资金方法必须被解析出来，<b>且 body 必须含预期 token</b>
 *    （只查方法名会被「空壳 body」骗过）；含 T-WITHDRAW 第一步的结算/记账入口；
 * ③ <b>反例</b> —— 合成输入覆盖：两写无事务 / 两写有事务 / 单写+Feign 与多写+Feign /
 *    类级事务 / <b>add*·increase*·settle*·payout* 新动词的两写无事务与有事务</b>。
 */
@DisplayName("资金写事务门禁 — 多资金写必须同事务；含 Feign 的必须登记；扫描面必须可信")
class FundTransactionGuardTest {

    // ─────────────────────────── 口径常量 ───────────────────────────

    /** 显式资金表（按表名转驼峰后出现在源码里也算「碰到资金」）。 */
    private static final List<String> FUND_TABLES = List.of(
            "wallet_account", "wallet_transaction", "pay_order", "refund_order", "reconciliation_batch");

    /** 资金仓储命名（**按调用的仓储识别，不看包名、不看类名**）。 */
    private static final List<Pattern> FUND_RECEIVER_PATTERNS = List.of(
            Pattern.compile("\\w*(Account|Transaction|PayOrder|RefundOrder|Reconciliation"
                    + "|Settle\\w*|Settlement\\w*|Points\\w*|Freeze\\w*|Frozen\\w*|Flow\\w*)\\w*"
                    + "(Repository|Mapper|Service|Client|Gateway|Port)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\w*(Ledger|Settlement)\\w*(Repository|Service|Mapper)",
                    Pattern.CASE_INSENSITIVE));

    /**
     * 写动词（前缀匹配：`createPending` / `markBalancePaidIfPending` 都要算）。
     *
     * <p><b>`add` / `increase` / `settle` / `payout` 是 2026-10-19 补进来的</b>（T-WITHDRAW 第一步）：
     * 该批新增的写法是 `settleConditionally` / `addSettled` / `increaseSettledTotal` / `addPaid` /
     * `increasePaidTotal` / `payout` —— 旧表一个都不认，于是「两个资金写 + 无事务」对门禁**不可见**。
     * 那正是 §5.5 的恒真空转形态：门禁绿是因为它没看见，不是因为它检查过。
     *
     * <p><b>代价是「人工 grep 推理」，不是门禁实跑</b>（本仓库那一次环境里 shell 不可用，门禁没被执行过）：
     * 新动词在资金命名 receiver 上的全部命中点已逐个核对 ——
     * <ol>
     *   <li>`pointsClient.addPoints`：仅 `OrderLifecycleManager#awardOrderRewardPoints` 与
     *       `NewUserRewardServiceAdapter#award` 两处，**每个方法各只有 1 次**（`pointsManager.addPoints`
     *       那几处 receiver 以 `Manager` 结尾、不匹配资金命名，不计）⇒ 都不到 2 的阈值；</li>
     *   <li>其余命中全是本批新增方法（`addSettled` / `addPaid` / `settleConditionally` 等）与
     *       列表操作（`add` / `addAll` 之类，receiver 不在资金命名内，不计）。</li>
     * </ol>
     * ⇒ 结论「不改变任何既有判定」是**推理结论**，不是跑出来的；反例由
     * {@link #shouldFlag_whenTwoAddStyleFundWritesWithoutTransaction()} 守住。
     */
    private static final List<String> WRITE_VERB_PREFIXES = List.of(
            "insert", "save", "update", "delete", "deduct", "credit", "mark", "create", "cancel",
            "void", "bind", "freeze", "unfreeze",
            "add", "increase", "settle", "payout");

    /** Feign 调用痕迹（receiver 名以 Client / Router 结尾）。 */
    private static final Predicate<String> IS_FEIGN_RECEIVER =
            name -> name.endsWith("Client") || name.endsWith("Router");

    /** 达到该本地资金写数就必须人工登记（含 Feign 时无法用事务保护）。 */
    private static final int LOCAL_WRITES_THRESHOLD = 2;

    /**
     * 扫到的源码文件数下界（判据 #16「数量断言」形态）。
     *
     * <p>定 800 的依据：路径归一化修复后实测为四位数（用 {@code JavaSourceParser.mainSources().size()}
     * 现场取，不在此复述）。取实测的约 2/3 —— 目的是抓「整体扫不到」，不是抓单个文件漏扫
     * （后者由已知清单断言覆盖）。
     */
    private static final int MIN_SCANNED_SOURCE_FILES = 800;

    /** 通过「资金预过滤」的文件数下界 —— 抓预过滤退化成「什么都不匹配」。 */
    private static final int MIN_PREFILTERED_FILES = 3;

    /** 扫描面断言①：方法必须被解析出来，且 body 必须含这些 token（区分「空 body」与「截断 body」）。 */
    private static final Map<String, List<String>> KNOWN_FUND_METHODS_AND_TOKENS = new LinkedHashMap<>();

    static {
        KNOWN_FUND_METHODS_AND_TOKENS.put("WalletManager#refund",
                List.of("existsByTransactionNo", "credit", "insert"));
        KNOWN_FUND_METHODS_AND_TOKENS.put("WalletManager#deductBalance",
                List.of("insert", "deduct"));
        KNOWN_FUND_METHODS_AND_TOKENS.put("PayManager#payByBalance",
                List.of("createPending", "deductBalance", "markBalancePaidIfPending"));
        // T-WITHDRAW 第一步（2026-10-19）：本批的结算/记账入口必须被解析出来，且 body 必须完整 ——
        // 若解析器在新包里漏切方法，「0 违规」就只是「没看见」。
        KNOWN_FUND_METHODS_AND_TOKENS.put("SettlementManager#settle",
                List.of("settleConditionally", "addSettled"));
        KNOWN_FUND_METHODS_AND_TOKENS.put("SellerFundAccountRepositoryImpl#addSettled",
                List.of("increaseSettledTotal", "insert"));
        KNOWN_FUND_METHODS_AND_TOKENS.put("SellerFundAccountRepositoryImpl#addPaid",
                List.of("increasePaidTotal", "insert"));
    }

    /** 已登记豁免：条目必须当前仍在违规（陈旧即失败）。 */
    private static final Map<String, String> REGISTERED_EXEMPTIONS = Map.of(
            "PayManager#payByBalance",
            "本地写 3 次（createPending/cancelPending/markBalancePaidIfPending），中间必须夹 Feign 扣款；"
                    + "每个本地写都是单语句、各自原子，无法也不该包在一个事务里。"
                    + "★ 依赖 mark-failed 可见性修复落地：markBalancePaidIfPending 返回 false 时"
                    + "「钱已扣、单未置成功」当前仅 log.warn —— 结构化审计日志上线前残留风险是静默漏账。");

    // ─────────────────────────── 主用例 ───────────────────────────

    @Test
    @DisplayName("仓库主源码 — 多资金写必须同事务，或已登记豁免")
    void shouldRequireTransactionForMultiFundWrites() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources();
        // 扫描面断言①-a：扫不到源码时下面所有断言都会「空集通过」= 假绿
        assertThat(sources)
                .as("扫描面异常缩小：源文件数 < " + MIN_SCANNED_SOURCE_FILES
                        + " ⇒ 门禁在空转（真实缺陷形态：路径未归一化导致扫到 0 个文件）")
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_SOURCE_FILES);

        ScanResult result = scan(sources);

        assertThat(result.violations())
                .as("多资金写要么同事务、要么登记豁免；静默通过不允许")
                .isEmpty();

        // 扫描面断言①-b：预过滤不许把扫描面吃空（否则「0 违规」只是「没看见」）
        assertThat(result.prefilteredFiles())
                .as("资金预过滤只命中 " + result.prefilteredFiles() + " 个文件：预过滤已退化，门禁在空转")
                .isGreaterThanOrEqualTo(MIN_PREFILTERED_FILES);

        // 扫描面断言②：绿且无豁免条目 ⇒ 红（没有任何多写被识别，说明解析器跳过了真实违规）
        assertThat(REGISTERED_EXEMPTIONS)
                .as("当前全绿却无任何豁免条目：解析器很可能跳过了真实违规")
                .isNotEmpty();

        List<String> stale = REGISTERED_EXEMPTIONS.keySet().stream()
                .filter(key -> !result.scannedKeys().contains(key)).toList();
        assertThat(stale)
                .as("陈旧豁免：条目已不违规（或方法不存在）—— 删掉它，否则豁免表会变成免责名单")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 扫描面断言② — 已知资金方法必须被解析出来，且 body 必须完整（不许空壳/截断）")
    void shouldParseKnownFundMethodsWithCompleteBody() throws IOException {
        Map<String, MethodDeclaration> parsed = new LinkedHashMap<>();
        for (Path file : JavaSourceParser.mainSources()) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            for (MethodDeclaration method : JavaSourceParser.methods(unit)) {
                parsed.putIfAbsent(keyOf(file, method.getNameAsString()), method);
            }
        }

        List<String> problems = new ArrayList<>();
        KNOWN_FUND_METHODS_AND_TOKENS.forEach((key, tokens) -> {
            MethodDeclaration method = parsed.get(key);
            if (method == null) {
                problems.add(key + " —— 方法未被解析出来");
                return;
            }
            String bodyText = JavaSourceParser.body(method);
            for (String token : tokens) {
                if (!bodyText.contains(token)) {
                    problems.add(key + " —— body 缺 token " + token + "（body 长度 "
                            + bodyText.length() + "，疑似截断或空壳）");
                }
            }
        });

        assertThat(problems)
                .as("body 不完整时资金写计数会偏低甚至为 0 ⇒ 门禁的绿是假的")
                .isEmpty();
    }

    // ─────────────────────────── 反例断言（合成输入） ───────────────────────────

    @Test
    @DisplayName("★ 用例1 — 同方法两个资金写且无事务 → 红")
    void shouldFlag_whenTwoFundWritesWithoutTransaction() {
        assertThat(violationsOf(sample("void bad() {\n"
                + "        walletTransactionRepository.insert(tx);\n"
                + "        walletAccountRepository.deduct(userId, amount);\n"
                + "    }")))
                .as("这正是 deductBalance 修复前的形态：插流水 + 扣余额，无事务 = 假流水")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 用例2 — 同方法两个资金写但带 @Transactional → 通过")
    void shouldPass_whenTwoFundWritesWithTransaction() {
        assertThat(violationsOf(sample("@Transactional(rollbackFor = Exception.class)\n"
                + "    void good() {\n"
                + "        walletTransactionRepository.insert(tx);\n"
                + "        walletAccountRepository.deduct(userId, amount);\n"
                + "    }"))).isEmpty();
    }

    @Test
    @DisplayName("★ 用例3 — 单写 + Feign 通过；含 Feign 的多写必须红")
    void shouldDistinguishFeignSingleWriteFromMultiWrite() {
        assertThat(violationsOf(sample("void c() {\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "    }")))
                .as("单语句自提交即原子，含 Feign 也通过")
                .isEmpty();

        assertThat(violationsOf(sample("void d() {\n"
                + "        payOrderRepository.createPending(orderNo);\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "        payOrderRepository.markBalancePaidIfPending(1L);\n"
                + "    }")))
                .as("Feign 不能进事务 ⇒ 这种多写无法用事务保护，必须登记理由")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 用例4（新）— 类级 @Transactional 覆盖其方法；方法级注解不被类级遮蔽")
    void shouldHonourClassLevelTransaction() {
        String classLevel = "@Transactional(rollbackFor = Exception.class)\n"
                + "class T { void m() { walletAccountRepository.deduct(1L, 1);"
                + " walletTransactionRepository.insert(null); } }";
        assertThat(violationsOf(classLevel))
                .as("类级注解 = 全类方法视同带注解")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 用例5（新）— add*/increase*/settle*/payout* 形态：两写无事务必须红")
    void shouldFlag_whenTwoAddStyleFundWritesWithoutTransaction() {
        assertThat(violationsOf(sample("void f() {\n"
                + "        sellerFundAccountRepository.addSettled(1L, 100);\n"
                + "        settlementRepository.settleConditionally(1L, 1, 2, today, now);\n"
                + "    }")))
                .as("T-WITHDRAW 第一步的真实写法：`addSettled` 与 `settleConditionally` 相加是一对资金写，"
                        + "无事务就该红。这条用例是那四个新动词的**唯一反例** —— 没有它，补了词表也可能恒不命中")
                .hasSize(1);

        assertThat(violationsOf(sample("void g() {\n"
                + "        sellerFundAccountRepository.addPaid(1L, 100);\n"
                + "        sellerFundAccountMapper.increasePaidTotal(1L, 100);\n"
                + "    }")))
                .as("`addPaid` + `increasePaidTotal`（打款面的真实组合）无事务同样必须红")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 用例6（新）— 同一组 add*/settle* 写法带 @Transactional 则通过（防止新动词一律判红）")
    void shouldPass_whenAddStyleFundWritesAreTransactional() {
        assertThat(violationsOf(sample("@Transactional(rollbackFor = Exception.class)\n"
                + "    void h() {\n"
                + "        sellerFundAccountRepository.addSettled(1L, 100);\n"
                + "        settlementRepository.settleConditionally(1L, 1, 2, today, now);\n"
                + "    }")))
                .as("新动词必须和旧动词一样「有事务就放行」，否则门禁会逼人写豁免")
                .isEmpty();
    }

    // ─────────────────────────── 纯函数 ───────────────────────────

    record ScanResult(List<String> violations, List<String> scannedKeys, int prefilteredFiles) { }

    /** 全仓扫描（纯函数，供用例与自证共用）。 */
    static ScanResult scan(List<Path> sources) throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> scannedKeys = new ArrayList<>();
        int prefiltered = 0;
        for (Path file : sources) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            if (!touchesFund(unit)) {
                continue;
            }
            prefiltered++;
            boolean classLevelTx = JavaSourceParser.typeHasAnnotation(unit, "Transactional");
            for (MethodDeclaration method : JavaSourceParser.methods(unit)) {
                int fundWrites = countFundWrites(method);
                if (fundWrites < LOCAL_WRITES_THRESHOLD) {
                    continue;
                }
                String key = keyOf(file, method.getNameAsString());
                scannedKeys.add(key);
                if (classLevelTx || JavaSourceParser.hasAnnotation(method, "Transactional")) {
                    continue;
                }
                if (REGISTERED_EXEMPTIONS.containsKey(key)) {
                    continue;
                }
                violations.add(key + " 有 " + fundWrites + " 个资金写"
                        + (containsFeignCall(method)
                                ? "且含 Feign：无法用事务保护，必须登记豁免理由"
                                : "且无 @Transactional：失败会留下半截账"));
            }
        }
        return new ScanResult(violations, scannedKeys, prefiltered);
    }

    /** 单个合成源码样本的违规判定（供反例用例使用）。 */
    static List<String> violationsOf(String source) {
        CompilationUnit unit = JavaSourceParser.parse(source);
        if (!touchesFund(unit)) {
            return List.of();
        }
        boolean classLevelTx = JavaSourceParser.typeHasAnnotation(unit, "Transactional");
        List<String> out = new ArrayList<>();
        for (MethodDeclaration method : JavaSourceParser.methods(unit)) {
            if (classLevelTx || JavaSourceParser.hasAnnotation(method, "Transactional")) {
                continue;
            }
            if (countFundWrites(method) >= LOCAL_WRITES_THRESHOLD) {
                out.add(method.getNameAsString());
            }
        }
        return out;
    }

    /** key 的唯一生成处：文件名#方法名（解析侧 / 违规项 / 豁免查表 / 陈旧校验 四处共用）。 */
    static String keyOf(Path file, String methodName) {
        return file.getFileName().toString().replace(".java", "") + "#" + methodName;
    }

    /**
     * 资金写计数（**AST 判定**）：receiver 是单个标识符 + 名字像资金仓储 + 方法名是写动词。
     *
     * <p>与旧实现的口径等价，但不再受书写格式影响：链式调用（receiver 是另一个调用）不算、
     * 实体对象（`payOrder.markPaid`）不算、跨行书写照算。
     */
    static int countFundWrites(MethodDeclaration method) {
        return JavaSourceParser.countFundWrites(method,
                name -> FUND_RECEIVER_PATTERNS.stream().anyMatch(p -> p.matcher(name).matches()),
                name -> WRITE_VERB_PREFIXES.stream().anyMatch(name::startsWith) || isExactWriteVerb(name));
    }

    /** 动词表的精确形态（前缀匹配之外，`void`/`bind` 这类短词避免误匹配 `voidXxx`）。 */
    private static boolean isExactWriteVerb(String name) {
        return name.equals("void") || name.equals("bind") || name.equals("freeze") || name.equals("unfreeze");
    }

    /** 方法体内是否出现 Feign 调用（receiver 以 Client / Router 结尾）。 */
    static boolean containsFeignCall(MethodDeclaration method) {
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            String receiver = call.getScope()
                    .filter(scope -> scope instanceof NameExpr)
                    .map(scope -> ((NameExpr) scope).getNameAsString())
                    .orElse("");
            if (IS_FEIGN_RECEIVER.test(receiver)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文件级预过滤（**只做加速，判据全在 AST 上**）。
     *
     * <p>为什么预过滤可以留在文本层：它是**超集** —— 注释/字符串里出现资金词也照样放行，
     * 因此只可能多扫、不可能漏扫。反过来若把它做成「精确过滤」，就等于把判据移到了正则上
     * （正是本轮要摆脱的东西）。为了让「预过滤退化成空」不被误读成「仓库很干净」，
     * 调用点对通过数另有一条下界断言。
     */
    static boolean touchesFund(CompilationUnit unit) {
        String text = unit.toString();
        return FUND_TABLES.stream().anyMatch(table -> text.contains(toCamel(table)))
                || FUND_RECEIVER_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    private static String toCamel(String table) {
        String[] parts = table.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    /** 合成样本外壳：只提供 receiver，不提供注解（由各用例自行加）。 */
    private static String sample(String body) {
        return "@Service\nclass Sample {\n"
                + "    private final WalletAccountRepository walletAccountRepository = null;\n"
                + "    private final WalletTransactionRepository walletTransactionRepository = null;\n"
                + "    private final PayOrderRepository payOrderRepository = null;\n"
                + "    private final PaymentChannelRouter channelRouter = null;\n"
                + "    private final SellerFundAccountRepository sellerFundAccountRepository = null;\n"
                + "    private final SellerFundAccountMapper sellerFundAccountMapper = null;\n"
                + "    private final SettlementRepository settlementRepository = null;\n"
                + "    " + body + "\n}\n";
    }
}
