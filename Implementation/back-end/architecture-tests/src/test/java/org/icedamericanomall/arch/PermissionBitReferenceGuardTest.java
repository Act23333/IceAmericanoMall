package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>权限位引用门禁</b>（拆位前必做，2026-10-18）—— 让「注解里写了一个不存在的位」变成构建失败。
 *
 * <h2>为什么需要它（现有门禁的盲区）</h2>
 * {@code AdminEndpointAuthorizationGuardTest} 只检查「有没有 {@code @PreAuthorize}」，
 * <b>不检查注解里那个字符串是否真实存在</b>。于是
 * {@code @PreAuthorize("@ss.hasPermi('order:wrtie')")}（拼错）会让门禁全绿，
 * 而线上表现为<b>永远 403</b> —— 拆位要批量改注解，笔误是必然事件，不是意外。
 *
 * <h2>① 解析边界：只认 {@code @ss.hasPermi('<单参>')}，复杂表达式直接失败</h2>
 * 这一条是刻意的<b>能力限制</b>，不是偷懒：本门禁需要把一个 SpEL 表达式静态解析成「引用了哪些位」。
 * 只要允许 {@code hasAnyPermi} / {@code hasRole} / {@code and} / {@code or} / 字符串拼接，
 * 解析就得跟着 SpEL 语法走，而<b>解析器一旦和被解析的语法不同步，门禁就会变成假绿</b>。
 * 所以：<b>看不懂就报错</b>。真需要复杂表达式时，先扩展本门禁（连同解析测试一起），再写表达式。
 *
 * <h2>② 双向比对刻意不对称</h2>
 * <table border="1">
 *   <caption>两个方向的要求强度不同，因为失败后果不同</caption>
 *   <tr><th>方向</th><th>强度</th><th>理由</th></tr>
 *   <tr>
 *     <td>注解引用了 → 种子里必须存在且 {@code status=1}</td>
 *     <td><b>硬失败</b></td>
 *     <td>引用不存在的位 = 该端点<b>永久 403</b>（fail-closed 会把它变成功能不可用），
 *         且只在运行时暴露</td>
 *   </tr>
 *   <tr>
 *     <td>种子里定义了 → 注解里没人用</td>
 *     <td><b>WARN + 白名单</b></td>
 *     <td>「先加位、后接注解」是迁移的正常中间态（本仓执行顺序就是这样），
 *         直接红会逼人跳步；但无限期零消费又说明位是废的，所以要用<b>白名单</b>记账：
 *         每条写清打算给谁用、什么时候接上，接上后必须删条目</td>
 *   </tr>
 * </table>
 *
 * <h2>③ 执行顺序（写死在这里，改顺序请先改这段）</h2>
 * <blockquote>
 * <b>先种子 → 再注解 → 后删旧位</b>
 * </blockquote>
 * <ol>
 *   <li><b>先种子</b>：新位必须先落 {@code sys_permission}（{@code status=1}），
 *       否则任何引用它的注解都会被本门禁判红 —— 这是刻意的，保证「注解不会指向虚空」；</li>
 *   <li><b>再注解</b>：逐控制器替换，本门禁兜底「引用必须存在」；</li>
 *   <li><b>后删旧位</b>：旧位<b>先 {@code status=0} 停用、不删行</b>（唯一选定的停用机制，见
 *       {@code rbac-permission-matrix-draft.md} §7），确认无引用后再谈物理删除。
 *       顺序反了会有一段时间「注解还指着旧位、但旧位已停用」= 全线 403。</li>
 * </ol>
 */
@DisplayName("权限门禁 — 注解引用的位必须真实存在（只认 hasPermi 单参）")
class PermissionBitReferenceGuardTest {

    /** 后端根目录（surefire 工作目录是 architecture-tests 模块根）。 */
    private static final Path BACKEND_ROOT = Paths.get("..");

    /** 种子文件（结构与 {@link RbacSeedEscalationGuardTest} 一致）。 */
    private static final Path INIT_SQL = Paths.get("..", "database", "Initialize.sql");

    /** 唯一允许的注解形态：单参 {@code hasPermi}。 */
    private static final Pattern ALLOWED_EXPRESSION =
            Pattern.compile("^\"@ss\\.hasPermi\\('([A-Za-z][A-Za-z0-9]*(?::[A-Za-z][A-Za-z0-9]*)+)'\\)\"$");

    private static final Pattern ANNOTATION_START = Pattern.compile("@PreAuthorize\\s*\\(");

    /**
     * <b>已弃用（待停用）的权限位</b>：位 → 取代它的新位 + 停用前置条件。
     *
     * <p>这是「后删旧位」那一步的**独立判据**：旧位的<b>注解引用必须归零</b>才可以停用。
     * 注意它与「零消费 WARN 归零」是两件事 —— WARN 说的是"新位还没接上"，本条说的是
     * "旧位还在被引用"。<b>WARN 归零不能替代本条</b>：完全可能出现"新位都接上了、
     * 但某个旧控制器还挂着旧位"的状态，那时 WARN 是 0 而旧位仍被引用。
     */
    private static final Map<String, String> DEPRECATED_BITS = Map.of(
            "user:admin", "被域位取代（user:read / user:write / order:write / aftersale:write / "
                    + "pay:admin / finance:admin / invoice:issue / invoice:void / currency:admin / "
                    + "marketing:read / marketing:admin / merchant:admin / home:admin / kg:admin + rbac:admin）。"
                    + "停用前置：注解引用归零（见 shouldHaveZeroReferencesToDeprecatedBits）");

    /** 反空断言下限：注解数量下限（扫描失效会掉到 0）。 */
    private static final int MIN_SCANNED_ANNOTATIONS = 10;

    /**
     * 零消费白名单：种子里定义了 {@code status=1}、但暂时没有注解引用的位。
     *
     * <p>只放<b>迁移中间态</b>与<b>待接线的存量位</b>，每条必须写清用途；一旦被引用，
     * 本门禁会要求删除条目（陈旧即失败），避免它长成第二张免责名单。
     */
    private static final Map<String, String> UNUSED_WHITELIST = Map.of(
            "*:*:*", "超级位：按设计永不被注解直接引用（注解只引用具体位），由 PermissionBits 通配规则生效",
            "ai:chat", "**为前端显隐预留**（不构成后端防护：所有登录用户都持该位）。"
                    + "前端接线（权限驱动菜单）后必须删掉本条",
            "order:read", "**为前端显隐预留**（同上；买家订单查询靠数据层 sellerId/userId 过滤，不靠该位）。"
                    + "前端接线后必须删掉本条",
            "product:read", "**为前端显隐预留**（同上；商品浏览是公开端点）。"
                    + "前端接线后必须删掉本条",
            "aftersale:write", "**预留**：售后两个写方法都同步触发退款，已按资金面挂 pay:admin；"
                    + "本位的目标是将来「不涉退款的售后状态操作」。若始终没有该场景，按 §7.1 停用（status=0）",
            "marketing:read", "**待前端接线**：要让「运营只看不改」需把营销域 GET 接本位；"
                    + "当前 marketing:admin 只由 ROLE_ADMIN 持有 ⇒ 没有只读运营档位。前端接线后删掉本条"
    );

    // ==================================================================================
    // ① 硬失败：注解引用的位必须存在于种子且 status=1
    // ==================================================================================

    @Test
    @DisplayName("硬失败方向 — 注解引用的位必须存在于 sys_permission 且 status=1")
    void shouldFailWhenAnnotationCitesMissingPermissionBit() {
        List<String> citations = scanCitations();
        assertThat(citations)
                .as("扫描到的 @PreAuthorize 少于 %d 处 —— 路径或谓词失效，门禁会静默空转", MIN_SCANNED_ANNOTATIONS)
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_ANNOTATIONS);

        Map<String, PermRow> seed = loadSeedRows();
        assertThat(seed).as("没解析到 sys_permission 种子 —— 门禁会静默空转").isNotEmpty();

        List<String> violations = new ArrayList<>(findUnknownCitations(citations, seed));
        assertThat(violations)
                .as("注解引用了种子中不存在（或已停用）的权限位 —— 该端点会永久 403。"
                        + "按执行顺序「先种子 → 再注解 → 后删旧位」补种子")
                .isEmpty();
    }

    // ==================================================================================
    // ①·二 「位存在」必须按 status=1 判，而不是「表里有这一行」
    // ==================================================================================

    /**
     * 停用位不得算「存在」。
     *
     * <p>这条看着显然，但很容易写错：{@code sys_permission} 里停用的位<b>行还在</b>（我们的停用机制是
     * {@code status=0} 而不是删行），所以「查得到这一行」≠「这个位可用」。
     * 若门禁按全量行判存在，就会出现：<b>注解指着一个已停用的位，门禁全绿，线上永久 403</b> ——
     * 恰好是这条门禁最该防的形态。
     */
    @Test
    @DisplayName("★ 位存在判定必须看 status=1 —— 停用位不得算作「存在」")
    void shouldTreatDisabledBitAsMissing() {
        Map<String, PermRow> seed = Map.of(
                "user:admin", new PermRow(5L, "user:admin", 0),        // 行还在，但已停用
                "seller:admin", new PermRow(6L, "seller:admin", 1));

        assertThat(findUnknownCitations(List.of("user:admin"), seed))
                .as("停用位被算作「存在」的话，迁移期注解指着旧位会静默通过")
                .hasSize(1);
        assertThat(findUnknownCitations(List.of("user:admin"), seed).get(0))
                .as("违规信息必须点明「已停用」，否则看的人会以为是自己拼错了")
                .contains("已停用");
        assertThat(findUnknownCitations(List.of("seller:admin"), seed))
                .as("启用位不得被误判")
                .isEmpty();
    }

    // ==================================================================================
    // ==================================================================================
    // ①·三 独立判据：「后删旧位」的前置是旧位引用归零（WARN 归零不能替代）
    // ==================================================================================

    /**
     * 旧位引用必须归零，才允许把 {@code status} 改成 0。
     *
     * <p>为什么必须独立成一条：一旦把旧位停用，{@link #shouldFailWhenAnnotationCitesMissingPermissionBit}
     * 也会红 —— 但那时已经晚了，中间态是<b>全线 403</b>。<b>本条跑在停用之前</b>，它回答的是
     * 「现在能不能停」；另一条回答的是「停用后有没有人还指着它」。
     *
     * <p>WARN 归零（新位都接上了）**不代表**旧位没人引用：完全可以新位全接、某个控制器还挂旧位。
     */
    @Test
    @DisplayName("★ 独立判据 — 旧位（user:admin）注解引用必须归零，才允许停用")
    void shouldHaveZeroReferencesToDeprecatedBits() {
        List<String> citations = scanCitations();
        assertThat(citations)
                .as("扫描到的 @PreAuthorize 少于 %d 处 —— 扫描失效会让本判据恒真", MIN_SCANNED_ANNOTATIONS)
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_ANNOTATIONS);

        Map<String, PermRow> seed = loadSeedRows();

        for (Map.Entry<String, String> entry : DEPRECATED_BITS.entrySet()) {
            String deprecated = entry.getKey();
            assertThat(seed)
                    .as("旧位 %s 已不在种子里（被物理删除？）—— 停用机制是 status=0 不删行，"
                            + "若确要删行请同时删掉 DEPRECATED_BITS 条目", deprecated)
                    .containsKey(deprecated);

            List<String> stillCited = citations.stream().filter(deprecated::equals).toList();
            PermRow row = seed.get(deprecated);
            if (row != null && row.status() == 0) {
                // 稳态不变量：旧位**已停用**后，注解引用必须为 0
                assertThat(stillCited)
                        .as("旧位 %s 已停用（status=0）却仍有 %d 处注解引用 —— 这些端点会永久 403",
                                deprecated, stillCited.size())
                        .isEmpty();
            } else {
                // 停用**前**的准入检查刻意不放门禁：若放这里，CI 在"第 5 步还没做"的整段期间常红，
                // 人就开始忽略红色。它落在 scripts/preflight-rbac-deprecated-bits.sh，
                // 引用未归零时退出非零 —— 那正是它的正常工作状态。
                System.out.println("[rbac-bits][INFO] " + deprecated + " 尚未停用，当前仍有 "
                        + stillCited.size() + " 处注解引用；停用前跑 "
                        + "scripts/preflight-rbac-deprecated-bits.sh（引用须归零）");
            }
        }
    }

    // ② 解析边界：复杂表达式必须直接失败（看不懂就报错，不许猜）
    // ==================================================================================

    @Test
    @DisplayName("解析边界 — 只允许 hasPermi 单参；hasAnyPermi / hasRole / and / or 一律失败")
    void shouldRejectComplexExpressions() {
        // 反向对照：合法形态必须被接受
        assertThat(extractSingleBit("\"@ss.hasPermi('user:admin')\""))
                .as("合法形态被拒绝的话，本门禁会把正常代码全判红")
                .isEqualTo("user:admin");

        // 这些都必须被拒（不是"忽略"，是硬失败）
        List<String> rejected = List.of(
                "\"@ss.hasAnyPermi('user:read','user:write')\"",
                "\"@ss.hasAllPermi('user:read','user:write')\"",
                "\"@ss.hasRole('ROLE_ADMIN')\"",
                "\"@ss.hasPermi('user:read') and @ss.hasPermi('user:write')\"",
                "\"@ss.hasPermi('user:' + 'admin')\"",
                "\"@ss.hasPermi('user:admin')\" " + "/* 注释 */",
                "\"@ss.hasPermi(\\\"user:admin\\\")\"");
        for (String expression : rejected) {
            assertThat(extractSingleBit(expression))
                    .as("复杂表达式必须被判为「不支持」而不是被猜测解析：%s", expression)
                    .isNull();
        }
    }

    // ==================================================================================
    // ③ 软方向：零消费 = WARN + 白名单（并保证白名单不陈旧）
    // ==================================================================================

    @Test
    @DisplayName("零消费方向 — 定义了但没人引用：WARN + 必须在白名单，且白名单陈旧即失败")
    void shouldWarnAndTrackUnusedBits() {
        Map<String, PermRow> seed = loadSeedRows();
        Set<String> cited = new LinkedHashSet<>(scanCitations());

        Set<String> definedActive = new TreeSet<>();
        seed.forEach((code, row) -> {
            if (row.status() == 1) {
                definedActive.add(code);
            }
        });

        Set<String> unused = new TreeSet<>(definedActive);
        unused.removeAll(cited);
        System.out.println("[rbac-bits] 已定义且启用 = " + definedActive.size()
                + " 个；被注解引用 = " + cited.size() + " 个；零消费 = " + unused);

        // 软方向：只打印 WARN，不判红（迁移中间态合法）
        for (String bit : unused) {
            if (UNUSED_WHITELIST.containsKey(bit)) {
                System.out.println("[rbac-bits][WARN] 零消费且已记账: " + bit
                        + " —— " + UNUSED_WHITELIST.get(bit));
            } else {
                System.out.println("[rbac-bits][WARN] 零消费且未记账: " + bit
                        + " —— 请把使用计划写进 UNUSED_WHITELIST，或确认它是废位");
            }
        }

        // 但白名单本身要严格：条目必须仍存在、且仍未被引用
        for (Map.Entry<String, String> entry : UNUSED_WHITELIST.entrySet()) {
            String bit = entry.getKey();
            assertThat(definedActive)
                    .as("陈旧白名单：种子中不存在启用位 %s（理由: %s）—— 删掉该条目", bit, entry.getValue())
                    .contains(bit);
            assertThat(cited)
                    .as("陈旧白名单：%s 已经被注解引用（理由: %s）—— 删掉该条目，否则白名单会变成免责名单",
                            bit, entry.getValue())
                    .doesNotContain(bit);
        }
    }

    // ==================================================================================
    // 判定（纯函数，可喂合成输入 —— 否则没法证明它真会咬）
    // ==================================================================================

    /** 找出「引用了不存在/已停用位」的引用。 */
    static List<String> findUnknownCitations(List<String> citations, Map<String, PermRow> seed) {
        List<String> violations = new ArrayList<>();
        for (String cited : new TreeSet<>(citations)) {
            PermRow row = seed.get(cited);
            if (row == null) {
                violations.add(cited + " —— 种子里没有这个位");
            } else if (row.status() != 1) {
                violations.add(cited + " —— 种子里已停用（status=" + row.status() + "）");
            }
        }
        return violations;
    }

    /** 把注解表达式解析成单个权限位；<b>不是单参 hasPermi 就返回 {@code null}（调用方必须失败）</b>。 */
    static String extractSingleBit(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher matcher = ALLOWED_EXPRESSION.matcher(expression.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    // ==================================================================================
    // 扫描
    // ==================================================================================

    /** 扫描源码里所有 {@code @PreAuthorize(...)} 的原文表达式，并校验解析边界。 */
    private static List<String> scanCitations() {
        List<SourceFile> files = scanJavaSources();
        assertThat(files).as("没扫到任何 Java 源文件 —— 路径失效").isNotEmpty();

        List<String> bits = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (SourceFile file : files) {
            for (String expression : extractAnnotationArguments(file.content())) {
                String bit = extractSingleBit(expression);
                if (bit == null) {
                    unsupported.add(file.relative() + " → " + expression);
                } else {
                    bits.add(bit);
                }
            }
        }
        assertThat(unsupported)
                .as("下列 @PreAuthorize 不是「hasPermi 单参」形态，本门禁拒绝猜测解析。"
                        + "要么改成单参（权限位拆分后用 hasAnyPermi 的场景请先扩展本门禁），"
                        + "要么扩展本门禁的解析能力并补解析测试")
                .isEmpty();
        return bits;
    }

    /**
     * 抽出每个 {@code @PreAuthorize} 的实参原文（含引号），按括号配对扫描。
     *
     * <p>不能用「取到下一个右括号」—— {@code hasAnyPermi('a','b')} 的右括号先闭合的是内层调用，
     * 那样会把表达式截断成半截，进而"看起来像"单参形态（假绿）。
     */
    static List<String> extractAnnotationArguments(String content) {
        String cleaned = stripComments(content);
        List<String> arguments = new ArrayList<>();
        Matcher start = ANNOTATION_START.matcher(cleaned);
        while (start.find()) {
            int open = cleaned.indexOf('(', start.start());
            if (open < 0) {
                continue;
            }
            int depth = 0;
            boolean inString = false;
            for (int i = open; i < cleaned.length(); i++) {
                char ch = cleaned.charAt(i);
                if (ch == '"' && (i == 0 || cleaned.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (inString) {
                    continue;
                }
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        arguments.add(cleaned.substring(open + 1, i).trim());
                        break;
                    }
                }
            }
        }
        return arguments;
    }

    private static String stripComments(String content) {
        String noBlock = content.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlock.replaceAll("(?m)//.*$", " ");
    }

    record SourceFile(Path path, String content) {
        String relative() {
            return path.toString().replace('\\', '/');
        }
    }

    private static List<SourceFile> scanJavaSources() {
        if (!Files.isDirectory(BACKEND_ROOT)) {
            throw new IllegalStateException("找不到后端根目录: " + BACKEND_ROOT.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.walk(BACKEND_ROOT)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(PermissionBitReferenceGuardTest::read)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("扫描源码失败", e);
        }
    }

    private static SourceFile read(Path path) {
        try {
            return new SourceFile(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("读取失败: " + path, e);
        }
    }

    // ==================================================================================
    // 种子解析（带 status）
    // ==================================================================================

    /** 一行 {@code sys_permission}。 */
    record PermRow(long id, String code, int status) {
    }

    /**
     * 解析 {@code sys_permission} 种子为「编码 → 行」。
     *
     * <p>按<b>元组切分</b>而不是一条大正则：一条正则吃不下可空的 {@code path} 与对齐全角空格的
     * 中文名称，容易静默漏行；切分失败的行会被跳过，靠下面的行数断言兜底。
     */
    private static Map<String, PermRow> loadSeedRows() {
        if (!Files.isRegularFile(INIT_SQL)) {
            throw new IllegalStateException("找不到 " + INIT_SQL.toAbsolutePath());
        }
        String sql;
        try {
            sql = Files.readString(INIT_SQL, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 Initialize.sql 失败", e);
        }

        Map<String, PermRow> rows = new LinkedHashMap<>();
        Matcher stmt = Pattern.compile(
                "INSERT INTO `sys_permission`[^;]*?VALUES(.*?)(?:ON DUPLICATE KEY|;)",
                Pattern.DOTALL).matcher(sql);
        while (stmt.find()) {
            for (List<String> fields : splitTuples(stmt.group(1))) {
                if (fields.size() < 7) {
                    continue;
                }
                Long id = parseLong(fields.get(0));
                String code = unquote(fields.get(2));
                Integer status = parseLong(fields.get(6)) == null ? null : parseLong(fields.get(6)).intValue();
                if (id != null && code != null && status != null) {
                    rows.put(code, new PermRow(id, code, status));
                }
            }
        }
        assertThat(rows).as("sys_permission 解析出的行数过少 —— 切分逻辑可能失效").hasSizeGreaterThanOrEqualTo(6);
        return rows;
    }

    /** 把 {@code (a,b),(c,d);} 切成字段列表的列表（忽略引号内的逗号与括号）。 */
    private static List<List<String>> splitTuples(String values) {
        List<List<String>> tuples = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            char ch = values.charAt(i);
            if (ch == '\'' ) {
                inString = !inString;
            }
            if (!inString && ch == '(') {
                depth++;
                if (depth == 1) {
                    current.setLength(0);
                    continue;
                }
            }
            if (!inString && ch == ')') {
                depth--;
                if (depth == 0) {
                    tuples.add(splitFields(current.toString()));
                    current.setLength(0);
                    continue;
                }
            }
            if (depth >= 1) {
                current.append(ch);
            }
        }
        return tuples;
    }

    private static List<String> splitFields(String tuple) {
        List<String> fields = new ArrayList<>();
        boolean inString = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < tuple.length(); i++) {
            char ch = tuple.charAt(i);
            if (ch == '\'') {
                inString = !inString;
            }
            if (!inString && ch == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            fields.add(current.toString().trim());
        }
        return fields;
    }

    private static String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Long parseLong(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.matches("\\d+") ? Long.parseLong(trimmed) : null;
    }
}
