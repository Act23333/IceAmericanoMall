package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p><b>判定口径（三条同时满足才红）</b>：
 * <ol>
 *   <li><b>同一方法内</b>有 ≥2 个资金表写操作（不是整个类计数 —— 类计数会把
 *       "一个方法写一次、另一个方法写一次"误判成需要事务）；</li>
 *   <li>该方法<b>没有</b> {@code @Transactional}（方法级或类级都没有）；</li>
 *   <li>该方法<b>没有</b> Feign 调用 —— 含 Feign 的方法<b>本来就不允许</b>有事务
 *       （跨服务调用放在事务里会长时间持有连接、且远端失败无法本地回滚），
 *       这是<u>有原则的豁免</u>，不是白名单。</li>
 * </ol>
 *
 * <p><b>资金类按"调用的表"识别，不按包名</b>：只要写操作落在资金表
 * （{@code wallet_account} / {@code wallet_transaction} / {@code pay_order} /
 * {@code refund_order} / {@code reconciliation_batch}）上就算资金写，
 * 不看它在 wallet-service 还是 pay-service，也不看类名里有没有 Manager。
 *
 * <p><b>本门禁的边界（重要，别把它当"事务正确性"证明）</b>：
 * <ul>
 *   <li>只保证"注解写了"；<b>注解在 ≠ 事务生效</b> —— 自调用（{@code this.x()}）绕过 Spring 代理，
 *       本门禁防不住；</li>
 *   <li>不检查传播行为（{@code REQUIRES_NEW} 等）、只读标记、数据源是否正确；</li>
 *   <li>不检查"该事务该不该更细/该不该拆"——那是评审判断。</li>
 * </ul>
 * 实际生效仍需集成测试（真实事务管理器 + H2/Testcontainers），当前缺。
 */
@DisplayName("资金写事务门禁 — 同一方法 ≥2 个资金写且无 @Transactional（且不含 Feign）必须红")
class FundTransactionGuardTest {

    /** 资金表（按写入落点识别，与包名/类名无关）。 */
    private static final List<String> FUND_TABLES = List.of(
            "wallet_account", "wallet_transaction", "pay_order", "refund_order", "reconciliation_batch");

    /** 资金写操作的方法名动词（仓储/服务层命名约定）。 */
    private static final Pattern WRITE_CALL = Pattern.compile(
            "\\.(insert|save|update|delete|deduct|credit|mark|create|cancel|void|bind)\\w*\\s*\\(");

    /** Feign 调用痕迹：有它就不该有事务。 */
    private static final Pattern FEIGN_CALL = Pattern.compile("\\b\\w*(Client|Router)\\b\\.\\w+\\s*\\(");

    /** 修饰符可选：包级私有方法（`void bad()`）同样是目标，漏掉它们门禁就形同虚设。 */
    private static final Pattern METHOD = Pattern.compile(
            "\\b(?:(?:public|protected|private|static|final)\\s+)*[\\w<>\\[\\], ?]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional\\b");

    @Test
    @DisplayName("仓库主源码 — 每个方法都必须满足：资金写 ≥2 ⇒ 有 @Transactional（或含 Feign 而豁免）")
    void shouldRequireTransactionForMultiFundWrites() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainSources()) {
            String source = cleaned(Files.readString(file, StandardCharsets.UTF_8));
            if (!touchesFundTable(source)) {
                continue;
            }
            boolean classLevelTx = TRANSACTIONAL.matcher(classHeader(source)).find();
            for (Method method : methods(source)) {
                if (classLevelTx || method.transactional || method.body.contains("@Transactional")) {
                    continue;
                }
                int fundWrites = countFundWrites(method.body);
                if (fundWrites < 2) {
                    continue;
                }
                if (FEIGN_CALL.matcher(method.body).find()) {
                    continue;   // 有原则的豁免：含 Feign 的方法不得开事务
                }
                violations.add(file.getFileName() + "#" + method.name + " 有 " + fundWrites
                        + " 个资金写且无 @Transactional（失败会留下半截账）");
            }
        }

        assertThat(violations)
                .as("资金多写必须同事务；要豁免就给方法加 Feign 调用（那是真实设计），不要加白名单")
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
    @DisplayName("★ 用例3 — 两个资金写分散在不同方法（各一次）→ 通过；含 Feign → 豁免")
    void shouldPass_whenWritesSplitOrFeignPresent() {
        String split = sample("void a() { walletTransactionRepository.insert(tx); }\n"
                + "    void b() { walletAccountRepository.deduct(userId, amount); }");
        assertThat(violationsOf(split))
                .as("按方法判定而非按类：各写一次不需要事务")
                .isEmpty();

        String withFeign = sample("void c() {\n"
                + "        payOrderService.createPending(orderNo);\n"
                + "        channelRouter.deductBalance(userId, amount, bizRef);\n"
                + "        payOrderService.markBalancePaidIfPending(1L);\n"
                + "    }");
        assertThat(violationsOf(withFeign))
                .as("含 Feign 的方法不能有事务（跨服务调用持有连接、远端失败无法本地回滚）")
                .isEmpty();
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
            int fundWrites = countFundWrites(method.body);
            if (fundWrites >= 2 && !FEIGN_CALL.matcher(method.body).find()) {
                out.add(method.name);
            }
        }
        return out;
    }

    private static String sample(String body) {
        return "@Service\nclass Sample {\n"
                + "    private final WalletAccountRepository walletAccountRepository = null;\n"
                + "    private final WalletTransactionRepository walletTransactionRepository = null;\n"
                + "    " + body + "\n}\n";
    }

    private static boolean touchesFundTable(String source) {
        return FUND_TABLES.stream().anyMatch(table -> source.contains(table.replace('_', ' '))
                || source.contains(toCamel(table)));
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
            String receiver = body.substring(Math.max(0, matcher.end() - 200), matcher.end());
            boolean fundReceiver = FUND_TABLES.stream()
                    .anyMatch(table -> receiver.toLowerCase().contains(toCamel(table).toLowerCase()));
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

    private static final Map<String, String> UNUSED_PLACEHOLDER = Map.of();
}
