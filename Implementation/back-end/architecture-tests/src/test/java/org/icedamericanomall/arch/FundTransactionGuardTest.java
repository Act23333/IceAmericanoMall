package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资金类多写操作必须带 {@code @Transactional} 的门禁。
 *
 * <p><b>为什么需要它</b>：{@code WalletManager#deductBalance} 曾经"先插流水 → 再扣余额"却
 * <b>没有 {@code @Transactional}</b> —— 无事务时那是两次独立提交，扣款失败会留下
 * <b>假支出流水</b>（有流水、没扣钱，账实不符）。这类缺陷是"钱会错"级别，却没有门禁，
 * 只能靠人眼；而人眼在本仓已经被证明会漏。
 *
 * <p><b>判定口径（同一方法内）</b>：
 * <ol>
 *   <li>≥2 个资金写 + 无 {@code @Transactional} + <b>不含 Feign</b> ⇒ <b>红</b>（失败留半截账）；</li>
 *   <li>≥2 个资金写 + <b>含 Feign</b> ⇒ <b>也红</b>（Feign 不能进事务，这种多写因此<b>无法</b>用事务保护，
 *       必须人工登记豁免理由）。<b>静默豁免 = 看不见的例外</b>，与漏写注解同样危险；</li>
 *   <li>1 个资金写 + 含 Feign ⇒ 通过（单语句自提交即原子）。</li>
 * </ol>
 *
 * <p><b>资金类按"调用的表/仓储"识别，不按包名</b>：显式清单 + <b>模式兜底</b>。
 * 清单认"确定的"，模式认"像的"（结算/冻结/积分/流水），新增资金仓储不必改清单也能纳入；
 * 另有断言"模式必须覆盖清单"，防止模式退化。
 *
 * <p><b>本门禁的边界（别把它当"事务正确性"证明）</b>：只保证"注解写了" ——
 * <b>注解在 ≠ 事务生效</b>（自调用绕过 Spring 代理，本门禁防不住）；传播行为/只读/数据源也不查。
 * 实际生效仍需集成测试（真实事务管理器 + H2/Testcontainers），当前缺。
 */
@DisplayName("资金写事务门禁 — 同方法多资金写无事务必须红；含 Feign 的多写也必须登记，不许静默通过")
class FundTransactionGuardTest {

    /** 资金表（按写入落点识别，与包名/类名无关）。 */
    private static final List<String> FUND_TABLES = List.of(
            "wallet_account", "wallet_transaction", "pay_order", "refund_order", "reconciliation_batch");

    /** 模式兜底：覆盖清单之外"同类"的资金仓储（结算/冻结/积分/流水）。源码标识符多为小写开头，故忽略大小写。 */
    private static final List<Pattern> FUND_IDENTIFIER_PATTERNS = List.of(
            Pattern.compile("\\w*(Account|Transaction|PayOrder|RefundOrder|Reconciliation"
                    + "|Settle\\w*|Settlement\\w*|Points\\w*|Freeze\\w*|Frozen\\w*|Flow\\w*)\\w*"
                    + "(Repository|Mapper|Service|Client|Gateway|Port)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\w*(Ledger|Settlement)\\w*(Repository|Service|Mapper)\\b",
                    Pattern.CASE_INSENSITIVE));

    /** 资金写操作的方法名动词（仓储/服务层命名约定）。 */
    private static final Pattern WRITE_CALL = Pattern.compile(
            "\\.(insert|save|update|delete|deduct|credit|mark|create|cancel|void|bind|freeze|unfreeze)\\w*\\s*\\(");

    /** Feign 调用痕迹：有它就不该有事务（多写也就保护不了，只能登记）。 */
    private static final Pattern FEIGN_CALL = Pattern.compile("\\b\\w*(Client|Router)\\b\\.\\w+\\s*\\(");

    /** 修饰符可选：包级私有方法同样是目标，漏掉它们门禁就形同虚设。 */
    private static final Pattern METHOD = Pattern.compile(
            "\\b(?:(?:public|protected|private|static|final)\\s+)*[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional\\b");

    /** 达到该本地资金写数就必须人工登记（含 Feign 时无法用事务保护）。 */
    private static final int LOCAL_WRITES_UNDER_FEIGN_THRESHOLD = 2;

    @Test
    @DisplayName("仓库主源码 — 多资金写 ⇒ 有 @Transactional，或含 Feign 但已登记豁免")
    void shouldRequireTransactionForMultiFundWrites() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String source = cleaned(Files.readString(file, StandardCharsets.UTF_8));
            if (!touchesFundTable(source)) {
                continue;
            }
            boolean classLevelTx = TRANSACTIONAL.matcher(classHeader(source)).find();
            for (Method method : methods(source)) {
                if (classLevelTx || method.transactional) {
                    continue;
                }
                int fundWrites = countFundWrites(method.body);
                if (fundWrites < LOCAL_WRITES_UNDER_FEIGN_THRESHOLD) {
                    continue;
                }
                boolean hasFeign = FEIGN_CALL.matcher(method.body).find();
                violations.add(file.getFileName() + "#" + method.name + " 有 " + fundWrites
                        + " 个资金写" + (hasFeign
                        ? "且含 Feign：无法用事务保护，必须登记豁免理由"
                        : "且无 @Transactional：失败会留下半截账"));
            }
        }

        assertThat(violations)
                .as("多资金写要么同事务、要么登记豁免；静默通过不允许（要豁免就登记进 open-items，不是加白名单）")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 用例1 — 同方法两个资金写且无事务 → 必须红")
    void shouldFlag_whenTwoFundWritesWithoutTransaction() {
        String source = sample("void bad() {\n"
                + "        walletTransactionRepository.insert(tx);\n"
                + "        walletAccountRepository.deduct(userId, amount);\n"
                + "    }");

        assertThat(violationsOf(source))
                .as("这正是 deductBalance 修复前的形态：插流水 + 扣余额，无事务 = 假流水")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 用例2 — 同方法两个资金写但带 @Transactional → 通过")
    void shouldPass_whenTwoFundWritesWithTransaction() {
        String source = sample("@Transactional(rollbackFor = Exception.class)\n"
                + "    void good() {\n"
                + "        walletTransactionRepository.insert(tx);\n"
                + "        walletAccountRepository.deduct(userId, amount);\n"
                + "    }");

        assertThat(violationsOf(source)).isEmpty();
    }

    @Test
    @DisplayName("★ 用例3 — 单写 + Feign 通过；含 Feign 的多写必须红（不许静默豁免）")
    void shouldDistinguishFeignSingleWriteFromMultiWrite() {
        String singleWriteWithFeign = sample("void c() {\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "    }");
        assertThat(violationsOf(singleWriteWithFeign))
                .as("单写 + Feign：单语句自提交即原子，无需事务")
                .isEmpty();

        String multiWriteWithFeign = sample("void d() {\n"
                + "        payOrderRepository.createPending(orderNo);\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "        payOrderRepository.markBalancePaidIfPending(1L);\n"
                + "    }");
        assertThat(violationsOf(multiWriteWithFeign))
                .as("含 Feign 的多写无法用事务保护 ⇒ 必须人工登记，不能静默通过")
                .hasSize(1);
    }

    @Test
    @DisplayName("★ 用例4 — 两个资金写分散在不同方法（各一次）→ 通过")
    void shouldPass_whenWritesSplitAcrossMethods() {
        String split = sample("void a() { walletTransactionRepository.insert(tx); }\n"
                + "    void b() { walletAccountRepository.deduct(userId, amount); }");
        assertThat(violationsOf(split))
                .as("按方法判定而非按类：各写一次不需要事务")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 用例5 — 模式必须覆盖显式清单（模式退化会让新增资金表静默逃逸）")
    void shouldCoverEveryExplicitFundTableByPattern() {
        List<String> uncovered = FUND_TABLES.stream()
                .filter(table -> FUND_IDENTIFIER_PATTERNS.stream()
                        .noneMatch(pattern -> pattern.matcher(toCamel(table) + "Repository").find()))
                .toList();

        assertThat(uncovered)
                .as("清单里有、模式认不出的资金表 ⇒ 只靠清单识别，新增同类表会漏。补模式或改命名")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 用例6 — 模式兜底能认出新资金仓储（结算/冻结/积分/流水）")
    void shouldRecognizeNewFundRepositoriesByPattern() {
        for (String identifier : List.of("settlementRepository", "settleRuleService",
                "pointsLedgerRepository", "walletFreezeRepository", "cashFlowRepository")) {
            assertThat(touchesFundTable("class X { " + identifier + " f; }"))
                    .as("%s 应被模式识别为资金仓储", identifier)
                    .isTrue();
        }
    }

    // ─────────────────────────── 纯函数（便于用例锁定） ───────────────────────────

    static List<String> violationsOf(String source) {
        String cleaned = cleaned(source);
        if (!touchesFundTable(cleaned)) {
            return List.of();
        }
        boolean classLevelTx = TRANSACTIONAL.matcher(classHeader(cleaned)).find();
        List<String> out = new ArrayList<>();
        for (Method method : methods(cleaned)) {
            if (classLevelTx || method.transactional) {
                continue;
            }
            if (countFundWrites(method.body) >= LOCAL_WRITES_UNDER_FEIGN_THRESHOLD) {
                out.add(method.name);
            }
        }
        return out;
    }

    private static String sample(String body) {
        return "@Service\nclass Sample {\n"
                + "    private final WalletAccountRepository walletAccountRepository = null;\n"
                + "    private final WalletTransactionRepository walletTransactionRepository = null;\n"
                + "    private final PayOrderRepository payOrderRepository = null;\n"
                + "    private final PaymentChannelRouter channelRouter = null;\n"
                + "    " + body + "\n}\n";
    }

    static boolean touchesFundTable(String source) {
        boolean explicit = FUND_TABLES.stream().anyMatch(table -> source.contains(toCamel(table)));
        boolean byPattern = FUND_IDENTIFIER_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(source).find());
        return explicit || byPattern;
    }

    private static String toCamel(String table) {
        String[] parts = table.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private static int countFundWrites(String body) {
        int count = 0;
        Matcher matcher = WRITE_CALL.matcher(body);
        while (matcher.find()) {
            String receiverWindow = body.substring(Math.max(0, matcher.end() - 200), matcher.end());
            boolean fundReceiver = FUND_IDENTIFIER_PATTERNS.stream()
                    .anyMatch(pattern -> pattern.matcher(receiverWindow).find());
            if (fundReceiver) {
                count++;
            }
        }
        return count;
    }

    private static String classHeader(String source) {
        int index = source.indexOf("class ");
        return index > 0 ? source.substring(0, index) : "";
    }

    private static List<Method> methods(String source) {
        List<Method> out = new ArrayList<>();
        Matcher matcher = METHOD.matcher(source);
        while (matcher.find()) {
            int start = matcher.end() - 1;
            int depth = 0;
            for (int cursor = start; cursor < source.length(); cursor++) {
                char ch = source.charAt(cursor);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        String prefix = source.substring(Math.max(0, matcher.start() - 200), matcher.start());
                        String body = source.substring(start, cursor + 1);
                        out.add(new Method(matcher.group(1), body,
                                prefix.contains("@Transactional") || body.contains("@Transactional")));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static String cleaned(String source) {
        String noBlock = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//.*$", " ");
    }

    private static List<Path> mainSources() throws IOException {
        Path root = Path.of("..").toAbsolutePath();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/") || p.toString().contains("\\src\\main\\java\\"))
                    .filter(p -> !p.toString().contains("architecture-tests"))
                    .collect(Collectors.toList());
        }
    }

    private record Method(String name, String body, boolean transactional) { }
}
