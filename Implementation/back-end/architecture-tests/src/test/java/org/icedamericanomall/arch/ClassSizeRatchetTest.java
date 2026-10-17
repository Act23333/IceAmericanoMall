package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>类长度棘轮门禁</b> —— 把「类 ≤ 300 行」（{@code collaboration/09-constraints.md} 编码规则）从「靠人记」
 * 变成构建期检查，且对存量债务<b>只减不增</b>（与 {@link ArchitectureTest} 的 {@code FreezingArchRule} 同一思路）。
 *
 * <p>为什么不是一刀切：约束机械落地前已有 19 个 main 类超限（最大 trade {@code OrderRepositoryImpl} 581 行），
 * 一次拆完不现实、全部冻结又会固化债务。基线文件
 * {@code architecture-tests/src/test/resources/class-size-baseline.tsv} 的文件头写明「这是棘轮不是许可」。
 *
 * <p>四条规则，全部由<b>纯函数</b> {@link #evaluate} 判定：
 * <ol>
 *   <li><b>新增违规即红</b>：模块 {@code src/main/java} 下任一类 &gt; 300 行且不在基线 → 红（点名文件与行数）；</li>
 *   <li><b>不许增长</b>：实测 &gt; 基线 {@code maxLines} → 红；实测更小不红，但给出「可下调」提示；</li>
 *   <li><b>陈旧即红</b>：基线条目的文件已不存在、或已 ≤ 300 行 → 红（还清即删）；</li>
 *   <li><b>到期即红</b>：基线条目 {@code deadline} 非空且 &lt; 今天 → 红（限时豁免不许无限续期）。</li>
 * </ol>
 *
 * <p>{@link #evaluate(Map, List, LocalDate)} 只吃「实测行数 + 基线条目 + 今天」，不碰磁盘也不读时钟 ——
 * 于是四条规则可以各自用合成数据验证「真的会红」（见本类后 6 个测试）。只靠「真实仓库跑一遍全绿」
 * 无法证明门禁会咬人。行数口径 = 文本行数（{@code Files.lines} 计数，与 {@code wc -l} 一致）。
 */
@DisplayName("架构守卫 — 类长度棘轮（≤300 行，只减不增）")
class ClassSizeRatchetTest {

    /** 硬约束上限（方法 ≤ 50 行 / 类 ≤ 300 行）。 */
    static final int MAX_CLASS_LINES = 300;

    /** 扫描根：本模块目录的上一级 = back-end 根（surefire 工作目录 = architecture-tests/）。 */
    private static final Path BACK_END_ROOT = Paths.get("..");

    /** 基线资源（classpath 根下）。 */
    private static final String BASELINE_RESOURCE = "class-size-baseline.tsv";

    /** 反空规则：main 类实测 1267 个，低于此数说明扫描根/遍历失效，而不是「类变少了」。 */
    private static final int MIN_EXPECTED_MAIN_CLASSES = 1000;

    /** 一条基线条目（{@code deadline == null} = 只受规则 2/3 约束）。 */
    record BaselineEntry(String path, int maxLines, LocalDate deadline, String reason) {
    }

    /** 违规种类，与四条规则一一对应。 */
    enum Rule {
        NEW_OVER_SIZED,          // 规则 1：超 300 行且不在基线
        GREW_BEYOND_BASELINE,    // 规则 2：超过基线 maxLines（棘轮上涨）
        BASELINE_FILE_GONE,      // 规则 3a：基线条目的文件已不存在（删除/改名/移动）
        BASELINE_REDEEMED,       // 规则 3b：基线条目的文件已 ≤ 300 行（债务还清，条目该删）
        DEADLINE_EXPIRED         // 规则 4：限时豁免已到期
    }

    /** 一条违规：规则 + 路径 + 可操作说明。 */
    record Violation(Rule rule, String path, String detail) {
        String describe() {
            return "[" + rule + "] " + path + " —— " + detail;
        }
    }

    /** 判定结果：违规（红）+ 下调基线建议（不红）。 */
    record Report(List<Violation> violations, List<String> updateHints) {
        boolean isClean() {
            return violations.isEmpty();
        }

        List<Violation> violationsOf(Rule rule) {
            return violations.stream().filter(violation -> violation.rule() == rule).toList();
        }
    }

    // ==================================================================================
    // 纯函数：四条规则的唯一判定处（无 IO、无时钟）
    // ==================================================================================

    /**
     * 按四条规则判定一次。
     *
     * @param actualLines 实测行数（键 = 相对 back-end 根的 {@code /} 分隔路径）
     * @param baseline    基线条目
     * @param today       判定日（显式传入，规则 4 才可测）
     * @return 违规列表 + 下调基线的建议
     */
    static Report evaluate(Map<String, Integer> actualLines, List<BaselineEntry> baseline, LocalDate today) {
        List<Violation> violations = new ArrayList<>();
        List<String> updateHints = new ArrayList<>();

        Set<String> baselined = new LinkedHashSet<>();
        for (BaselineEntry entry : baseline) {
            if (!baselined.add(entry.path())) {
                throw new IllegalArgumentException("基线条目重复登记同一路径: " + entry.path());
            }
        }

        // 规则 1（按路径排序，失败信息稳定可复现）
        for (Map.Entry<String, Integer> actual : new TreeMap<>(actualLines).entrySet()) {
            String path = actual.getKey();
            int lines = actual.getValue();
            if (lines > MAX_CLASS_LINES && !baselined.contains(path)) {
                violations.add(new Violation(Rule.NEW_OVER_SIZED, path, lines + " 行 > " + MAX_CLASS_LINES
                        + " 行的硬上限，且不在基线内 —— 请按职责/用例族拆分；确需限时豁免必须在同一提交里"
                        + "登记进 " + BASELINE_RESOURCE + " 并填 deadline"));
            }
        }

        // 规则 2/3/4（保持基线登记顺序，便于对照文件阅读）
        for (BaselineEntry entry : baseline) {
            String path = entry.path();
            Integer actual = actualLines.get(path);

            if (actual == null) {
                violations.add(new Violation(Rule.BASELINE_FILE_GONE, path,
                        "基线条目对应的文件已不存在（删除/改名/移动）—— 棘轮不允许预留，请删除该条目"));
            } else if (actual <= MAX_CLASS_LINES) {
                violations.add(new Violation(Rule.BASELINE_REDEEMED, path, "实测 " + actual + " 行 ≤ "
                        + MAX_CLASS_LINES + " 行，债务已还清 —— 请从基线删除该条目（基线只减不增）"));
            } else if (actual > entry.maxLines()) {
                violations.add(new Violation(Rule.GREW_BEYOND_BASELINE, path, "实测 " + actual + " 行 > 基线 maxLines "
                        + entry.maxLines() + " 行 —— 棘轮只减不增；请拆回 " + entry.maxLines()
                        + " 行以内，或在本提交里显式上调 maxLines 让评审看见"));
            } else if (actual < entry.maxLines()) {
                updateHints.add("可下调 maxLines: " + path + " 基线登记 " + entry.maxLines() + " 行，实测已缩到 "
                        + actual + " 行（缩小不算违规，但基线应跟着收紧）");
            }

            if (entry.deadline() != null && entry.deadline().isBefore(today)) {
                violations.add(new Violation(Rule.DEADLINE_EXPIRED, path, "限时豁免已于 " + entry.deadline()
                        + " 到期（判定日 " + today + "）—— 请拆分该类并删除基线条目，不允许续期后继续挂着"));
            }
        }
        return new Report(List.copyOf(violations), List.copyOf(updateHints));
    }

    /**
     * 解析基线：{@code path <TAB> maxLines <TAB> deadline <TAB> reason}，{@code #} 注释与空行忽略。
     *
     * <p>格式错误 / 理由留空 / 路径重复一律抛 {@link IllegalArgumentException}：基线的「合法性」与「是否合规」
     * 分开管 —— 解析坏了必须炸，而不是静默丢掉一条棘轮。
     */
    static List<BaselineEntry> parseBaseline(List<String> lines) {
        List<BaselineEntry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            int lineNumber = index + 1;
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] columns = raw.split("\t", -1);
            if (columns.length != 4) {
                throw new IllegalArgumentException("基线第 " + lineNumber + " 行必须是 4 列"
                        + "（path\\tmaxLines\\tdeadline\\treason），实际 " + columns.length + " 列: " + raw);
            }
            String path = columns[0].trim();
            String deadlineText = columns[2].trim();
            if (path.isEmpty() || columns[1].trim().isEmpty() || columns[3].trim().isEmpty()) {
                throw new IllegalArgumentException("基线第 " + lineNumber + " 行的 path / maxLines / reason 必填: " + raw);
            }
            if (path.indexOf('\\') >= 0 || !seen.add(path)) {
                throw new IllegalArgumentException("基线第 " + lineNumber + " 行路径必须用 / 分隔且不得重复: " + path);
            }
            int maxLines;
            try {
                maxLines = Integer.parseInt(columns[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("基线第 " + lineNumber + " 行 maxLines 不是整数: " + columns[1], e);
            }
            LocalDate deadline = deadlineText.isEmpty() || "-".equals(deadlineText) ? null : LocalDate.parse(deadlineText);
            entries.add(new BaselineEntry(path, maxLines, deadline, columns[3].trim()));
        }
        return List.copyOf(entries);
    }

    // ==================================================================================
    // 真实门禁：读磁盘 → 调纯函数 → 断言空
    // ==================================================================================

    @Test
    @DisplayName("真实扫描 — 全部 main 类 ≤ 300 行，或已在只减不增的基线内（陈旧条目 / 到期豁免一并判红）")
    void shouldKeepEveryMainClassWithinRatchet() {
        Path root = BACK_END_ROOT.toAbsolutePath().normalize();
        Map<String, Integer> actualLines = measureMainClasses(root);
        List<BaselineEntry> baseline = parseBaseline(readBaselineResource());
        LocalDate today = LocalDate.now();

        // 反空规则：扫描根写错 / 遍历失效时，下面的「零违规」会伪装成全绿
        assertThat(actualLines).as("只扫到 %d 个 main 类（扫描根 = %s）—— 远低于预期，扫描路径或遍历已失效",
                actualLines.size(), root).hasSizeGreaterThan(MIN_EXPECTED_MAIN_CLASSES);
        assertThat(baseline).as("基线读出来是空 —— 规则 1 会退化成「一刀切全红」（存量债务尚未偿清，不允许）").isNotEmpty();

        List<Map.Entry<String, Integer>> overLimit = actualLines.entrySet().stream()
                .filter(entry -> entry.getValue() > MAX_CLASS_LINES)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
        System.out.println("[class-size-ratchet] 扫描 main 类 = " + actualLines.size() + " 个 / 基线条目 = "
                + baseline.size() + " 条 / 超 " + MAX_CLASS_LINES + " 行 = " + overLimit.size() + " 个");
        overLimit.forEach(entry -> System.out.println("[class-size-ratchet]   " + entry.getValue()
                + " 行  " + entry.getKey()));

        Report report = evaluate(actualLines, baseline, today);
        report.updateHints().forEach(hint -> System.out.println("[class-size-ratchet][update] " + hint));

        assertThat(report.violations())
                .as("类长度棘轮违规（判定日 %s，基线 = %s）：%n%s", today, BASELINE_RESOURCE,
                        report.violations().stream().map(Violation::describe).reduce("", (a, b) -> a + "\n  " + b))
                .isEmpty();
    }

    // ==================================================================================
    // 门禁自身的变异验证：四条规则各喂合成数据，证明「真的会红」
    // ==================================================================================

    @Test
    @DisplayName("规则 1（新增超限即红）— 合成：310 行未登记 → NEW_OVER_SIZED；恰好 300 行不判")
    void shouldFlagNewOverSizedClass_whenNotInBaseline() {
        Report report = evaluate(Map.of(
                "synthetic-service/src/main/java/x/Brand.java", 310,
                "synthetic-service/src/main/java/x/Exactly.java", MAX_CLASS_LINES), List.of(), LocalDate.of(2026, 10, 18));

        assertThat(report.violationsOf(Rule.NEW_OVER_SIZED))
                .as("新增超 300 行且未登记的类必须判红，且不得把「恰好 300 行」也算进去").hasSize(1);
        Violation violation = report.violationsOf(Rule.NEW_OVER_SIZED).get(0);
        assertThat(violation.path()).as("失败文案必须点名文件")
                .isEqualTo("synthetic-service/src/main/java/x/Brand.java");
        assertThat(violation.describe()).as("失败文案必须带实测行数与上限").contains("310").contains("300");
    }

    @Test
    @DisplayName("规则 2（不许增长）— 合成：实测 345 > 基线 344 → GREW_BEYOND_BASELINE；等值 344 不判")
    void shouldFlagGrowth_whenClassExceedsBaselineMaxLines() {
        String path = "synthetic-service/src/main/java/x/Grown.java";
        BaselineEntry entry = new BaselineEntry(path, 344, null, "合成数据：存量债务");

        Report grown = evaluate(Map.of(path, 345), List.of(entry), LocalDate.of(2026, 10, 18));
        assertThat(grown.violationsOf(Rule.GREW_BEYOND_BASELINE)).as("棘轮不许上涨").hasSize(1);
        assertThat(grown.violationsOf(Rule.GREW_BEYOND_BASELINE).get(0).describe())
                .contains(path).contains("345").contains("344");
        assertThat(evaluate(Map.of(path, 344), List.of(entry), LocalDate.of(2026, 10, 18)).isClean())
                .as("与基线等值不是增长").isTrue();
    }

    @Test
    @DisplayName("规则 2 的另一半（允许缩小）— 合成：实测 320 < 基线 344 → 不红，但提示可下调 maxLines")
    void shouldAllowAndHint_whenClassShrinksBelowBaselineMaxLines() {
        String path = "synthetic-service/src/main/java/x/Shrunk.java";
        Report report = evaluate(Map.of(path, 320),
                List.of(new BaselineEntry(path, 344, null, "合成数据：存量债务")), LocalDate.of(2026, 10, 18));

        assertThat(report.violations()).as("缩小不是违规（否则没人愿意还债）").isEmpty();
        assertThat(report.updateHints()).as("缩小时必须提示收紧基线，否则基线会漂成免责名单").hasSize(1);
        assertThat(report.updateHints().get(0)).as("提示里要能直接看到「路径 / 旧值 / 新值」")
                .contains(path).contains("344").contains("320").contains("下调");
    }

    @Test
    @DisplayName("规则 3（陈旧即红）— 合成：文件已删 → BASELINE_FILE_GONE；已缩到 300 行 → BASELINE_REDEEMED")
    void shouldFlagStaleBaselineEntries_whenFileDeletedOrAlreadyWithinLimit() {
        String redeemed = "synthetic-service/src/main/java/x/Redeemed.java";
        Report report = evaluate(Map.of(redeemed, MAX_CLASS_LINES), List.of(
                        new BaselineEntry("synthetic-service/src/main/java/x/Deleted.java", 344, null, "合成数据：文件将被删"),
                        new BaselineEntry(redeemed, 334, null, "合成数据：债务已还清")),
                LocalDate.of(2026, 10, 18));

        assertThat(report.violationsOf(Rule.BASELINE_FILE_GONE)).extracting(Violation::path)
                .as("文件不存在却还挂在基线上 → 必须红")
                .containsExactly("synthetic-service/src/main/java/x/Deleted.java");
        assertThat(report.violationsOf(Rule.BASELINE_REDEEMED)).extracting(Violation::path)
                .as("已 ≤ 300 行却还挂在基线上 → 必须红（棘轮只减不增，还清即删）").containsExactly(redeemed);
        assertThat(report.isClean()).isFalse();
    }

    @Test
    @DisplayName("规则 4（到期即红）— 合成：deadline 2026-11-15 在判定日 2026-11-16 → DEADLINE_EXPIRED；当天与留空不判")
    void shouldFlagExpiredDeadline_whenDeadlineBeforeToday() {
        String path = "pay-service/src/main/java/x/Exempt.java";
        BaselineEntry entry = new BaselineEntry(path, 344, LocalDate.of(2026, 11, 15), "合成数据：限时豁免");
        Map<String, Integer> actualLines = Map.of(path, 344);

        Report expired = evaluate(actualLines, List.of(entry), LocalDate.of(2026, 11, 16));
        assertThat(expired.violationsOf(Rule.DEADLINE_EXPIRED)).as("豁免已到期必须红").hasSize(1);
        assertThat(expired.violationsOf(Rule.DEADLINE_EXPIRED).get(0).describe()).contains(path).contains("2026-11-15");
        assertThat(evaluate(actualLines, List.of(entry), LocalDate.of(2026, 11, 15)).violations())
                .as("deadline 当天尚未到期（判定口径是 deadline < today）").isEmpty();
        assertThat(evaluate(actualLines,
                List.of(new BaselineEntry(path, 344, null, "合成数据：无豁免期")), LocalDate.of(2030, 1, 1)).violations())
                .as("deadline 留空 = 只受规则 2/3 约束，不因时间变红").isEmpty();
    }

    @Test
    @DisplayName("反向对照 — 合成：全部在限内 / 与基线等值 → 零违规零建议（证明判定不是恒红）")
    void shouldStayClean_whenEveryClassIsWithinLimitOrMatchesBaseline() {
        String path = "pay-service/src/main/java/x/Exempt.java";
        Report report = evaluate(Map.of(path, 344, "item-service/src/main/java/x/Fine.java", MAX_CLASS_LINES),
                List.of(new BaselineEntry(path, 344, LocalDate.of(2026, 11, 15), "合成数据：限时豁免")),
                LocalDate.of(2026, 11, 14));

        assertThat(report.violations()).as("判定恒红的话，上面 5 条变异单测什么也证明不了").isEmpty();
        assertThat(report.updateHints()).as("与基线等值不该产生下调建议").isEmpty();
    }

    // ==================================================================================
    // 基础设施：读磁盘 / 读基线 / 目录发现（门禁自身的唯一副作用面）
    // ==================================================================================

    /** 实测全部模块 {@code src/main/java/**}{@code /*.java} 的行数（键 = 相对 back-end 根的 / 分隔路径）。 */
    static Map<String, Integer> measureMainClasses(Path backEndRoot) {
        if (!Files.isRegularFile(backEndRoot.resolve("pom.xml"))) {
            throw new IllegalStateException("扫描根不像 back-end 根（缺 pom.xml）: " + backEndRoot
                    + " —— 请在 back-end 根目录执行 mvn -pl architecture-tests test（surefire 工作目录应为模块根）");
        }
        Map<String, Integer> measured = new TreeMap<>();
        for (Path module : listModules(backEndRoot)) {
            Path sourceRoot = module.resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                        .filter(Files::isRegularFile).toList()) {
                    measured.put(backEndRoot.relativize(file).toString().replace('\\', '/'), countLines(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("遍历源码失败: " + sourceRoot, e);
            }
        }
        return measured;
    }

    /** 模块发现：含 {@code pom.xml} 的直接子目录（不维护清单，新增模块自动纳入）。 */
    private static List<Path> listModules(Path backEndRoot) {
        try (Stream<Path> children = Files.list(backEndRoot)) {
            return children.filter(Files::isDirectory)
                    .filter(directory -> Files.isRegularFile(directory.resolve("pom.xml")))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("列出模块失败（扫描根 = " + backEndRoot + "）", e);
        }
    }

    /** 行数口径 = 文本行数（与 {@code wc -l} 一致，末尾换行不额外算一行）。 */
    private static int countLines(Path file) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return (int) lines.count();
        } catch (IOException e) {
            throw new UncheckedIOException("读取源码失败: " + file, e);
        }
    }

    private static List<String> readBaselineResource() {
        try (InputStream stream = ClassSizeRatchetTest.class.getClassLoader().getResourceAsStream(BASELINE_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少类长度基线资源: " + BASELINE_RESOURCE
                        + "（应位于 architecture-tests/src/test/resources/ 下）");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("读取基线资源失败: " + BASELINE_RESOURCE, e);
        }
    }
}
