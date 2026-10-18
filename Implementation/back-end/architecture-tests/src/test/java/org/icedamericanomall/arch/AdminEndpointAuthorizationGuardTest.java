package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>管理端/商家端端点的鉴权门禁</b>（RBAC0，2026-10-18）—— 让「新增端点忘了鉴权」变成构建失败。
 *
 * <h2>规则</h2>
 * <blockquote>
 * 类级 {@code @RequestMapping} 路径以 <code>/api/admin</code> 或 <code>/api/seller</code> 开头的控制器，
 * <b>必须带类级 {@code @PreAuthorize}</b>（出现在 {@code public class} 之前），或登记在 {@link #EXEMPT}。
 * </blockquote>
 *
 * <h2>为什么是「类级」而不是「每个方法」</h2>
 * 现阶段的权限粒度就是<b>一个控制器一个权限位</b>。类级要求有决定性好处：
 * <b>新增方法自动被覆盖</b>，不存在「加了个 {@code @GetMapping} 忘了加注解」这种形态。
 * 等权限矩阵细化到方法级时，本规则再收紧。
 *
 * <h2>Q5 裁决已落地（2026-10-18）：不再有「代码内守卫」这条豁免路径</h2>
 * 此前 {@code AdminPayController} / {@code AdminMerchantController} 因为所在服务**没有**
 * {@code spring-boot-starter-security}（{@code ia-common} 里是 {@code optional}），
 * {@code @PreAuthorize} 编译都过不了，只能用代码内 {@code assertAdmin()}。
 * 裁决是<b>补依赖、不做双轨</b>：四个服务（pay / merchant / ai / logistics）已引入依赖，
 * 两个控制器改为类级注解、{@code assertAdmin()} 已删除，豁免条目也已从本表移除。
 * <b>不要重新引入「代码内判定」这条豁免</b> —— 两套机制并存时，漏写注解的地方不会有任何提示，
 * 本次门禁上线抓到的两个控制器正是那个形态。
 *
 * <h2>豁免表纪律</h2>
 * 每条都要写清<b>为什么现在不能加注解</b>与<b>什么时候必须去掉</b>；且<b>陈旧即失败</b> ——
 * 类已不存在、或已补上注解，都必须从表里删除，否则白名单会膨胀成免责名单。
 * 现存条目全部是<b>待权限矩阵裁定</b>（商家端四个控制器：该挂哪位尚未执行）。
 *
 * <p>实现是<b>源码扫描</b>（不是字节码）：{@code architecture-tests} 不依赖各业务模块，
 * 扫源码可跨全部服务，且不需要 reactor 先编译它们。<b>判定前必须先剥注释</b>（见 {@link SourceFile}）。
 */
@DisplayName("架构守卫 — /api/admin 与 /api/seller 控制器必须有类级 @PreAuthorize")
class AdminEndpointAuthorizationGuardTest {

    /** 受管路径前缀（管理端、商家端）。 */
    private static final String[] GUARDED_PREFIXES = {
            "/api/admin", "/api/seller",
            // 盲区补丁（批 3 实测发现）：管理端控制器不一定挂在 /api/admin 下 ——
            // AdminOrderController 的路径是 /api/trade/admin，此前**从未被本门禁覆盖**。
            // 教训：受管前缀不能按"看起来像管理端"猜，要按实际 RequestMapping 扫。
            "/api/trade/admin",
            // 同族盲区（2026-10-18 全量扫描发现）：商家侧的贸易端控制器也挂在 /api/trade/seller 下，
            // 不在 /api/seller 前缀内 ⇒ 同样从未被本门禁覆盖。
            "/api/trade/seller"};

    /**
     * <b>平台级配置前缀</b>（Q8 裁决 2026-10-18 扩的）：挂在业务资源段下、但语义是"平台配置"的写端点。
     *
     * <p>为什么单独一组：这些控制器里**同一个类既有公开读、又有平台写**
     * （如 {@code /api/item/category} 的 {@code GET /tree} 是公开的），
     * 所以<b>不能用类级注解</b>（会连带封掉公开读）。本组允许「方法级覆盖」：
     * 要求 {@code @PreAuthorize} 出现次数 ≥ 写映射方法数。
     *
     * <p><b>已知近似</b>：这是<b>计数</b>而非"逐方法绑定"——理论上可能把注解挂在读方法上凑数。
     * 逐方法绑定需要真正的源码解析器，本批不做；真要更严时先扩本门禁并补解析测试
     * （与「看不懂就报错」同一条纪律：能力不足要说出来，不能假装守住了）。
     */
    private static final String[] PLATFORM_CONFIG_PREFIXES = {
            "/api/item/category", "/api/item/brand", "/api/item/attr"};

    /**
     * <b>允许方法级覆盖的管理端前缀</b>：同一个控制器里不同方法要**不同的位**，
     * 因此不能类级（Spring 的类级与方法级是 <b>AND 而非覆盖</b>，类级 + 方法级会要求两个位都有）。
     *
     * <p>本组要求：{@code @PreAuthorize} 数 ≥ 全部映射方法数（管理端没有公开端点，不许漏）。
     */
    private static final String[] METHOD_LEVEL_ADMIN_PREFIXES = {
            "/api/trade/admin",
            // 批 4：售后管理端**必须**按方法（查询 aftersale:read / 两个写方法 pay:admin）——
            // 整类挂任一位都是错的：挂 pay:admin 让"只看列表"的客服拿到退款权，
            // 挂 aftersale:read 则写不了。所以它必须进本集合，而不是被"必须有类级注解"卡住。
            "/api/admin/after-sale"};

    /** 写操作的映射注解（用于平台配置前缀的方法级覆盖计数）。 */
    private static final String[] WRITE_MAPPINGS = {"@PostMapping", "@PutMapping", "@DeleteMapping", "@PatchMapping"};

    /** 反空断言下限：受管控制器数量的下限（谓词失效会掉到 0）。 */
    private static final int MIN_GUARDED_CONTROLLERS = 15;

    /** 类级路径的匹配：只取 {@code public class} 之前的注解区。 */
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]+)\"");

    /**
     * 豁免表（简单类名 → 理由 + 截止）。<b>全部是「待权限矩阵执行」</b>，见
     * {@code rbac-permission-matrix-draft.md}；裁定并执行后应补注解并删条目。
     */
    private static final Map<String, String> EXEMPT = buildExempt();

    private static Map<String, String> buildExempt() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("SellerFinanceController",
                "待矩阵执行（截止 2026-11-15）：端点由 UserContext 取 sellerId 自证身份，但没有声明式权限位；"
                        + "Q3 已裁决拆三位，待执行（seller:finance）");
        table.put("MerchantCouponController",
                "待矩阵执行（截止 2026-11-15）：Q3 已裁决拆三位，待执行（seller:coupon）");
        table.put("KnowledgeBaseController",
                "待矩阵执行（截止 2026-11-15）：控制器内有显式 UserContext.hasRole(ROLE_SELLER)，"
                        + "属「代码内判定」不可 grep 审计；按 Q3 应改为声明式 seller:knowledge");
        table.put("MerchantController",
                "待矩阵执行（截止 2026-11-15）：**同类里有一条必须永久豁免** —— POST /api/seller/apply 是"
                        + "入驻申请（Q4 裁决：无权限位，任何登录用户可调），不能挂 seller:admin；"
                        + "其余端点属商家自有店铺");
        return Map.copyOf(table);
    }

    // ==================================================================================
    // ① 真实扫描：受管控制器都必须有类级 @PreAuthorize（或已登记豁免）
    // ==================================================================================

    @Test
    @DisplayName("真实扫描 — 受管控制器必须有类级 @PreAuthorize（反空断言 + 豁免陈旧即红）")
    void shouldRequireClassLevelPermission_whenControllerServesAdminOrSellerPaths() {
        List<SourceFile> guarded = guardedControllers();
        assertThat(guarded)
                .as("受管的 /api/admin|/api/seller 控制器少于 %d 个 —— 谓词或扫描目录可能失效", MIN_GUARDED_CONTROLLERS)
                .hasSizeGreaterThanOrEqualTo(MIN_GUARDED_CONTROLLERS);

        List<String> violations = new ArrayList<>();
        for (SourceFile source : guarded) {
            if (EXEMPT.containsKey(source.simpleName())) {
                continue;
            }
            if (isViolation(source)) {
                violations.add(source.relative()
                        + "（映射 " + source.classMapping() + "）缺类级 @PreAuthorize"
                        + "（平台配置前缀允许方法级覆盖：写方法数 > @PreAuthorize 数）");
            }
        }
        assertThat(violations)
                .as("下列管理端/商家端控制器没有类级 @PreAuthorize —— 新增端点会静默无鉴权；"
                        + "请补 `@PreAuthorize(\"@ss.hasPermi('…')\")`，或在 EXEMPT 登记并对齐权限矩阵草案。"
                        + "注意：若该服务缺少 spring-boot-starter-security，注解会被静默忽略（安全剧场）")
                .isEmpty();
    }

    // ==================================================================================
    // ② 豁免表必须「陈旧即失败」
    // ==================================================================================

    @Test
    @DisplayName("豁免表 — 每条必须仍存在且仍缺注解（已补注解 / 类已删除 都红）")
    void shouldFailWhenExemptionIsStale() {
        List<SourceFile> scanned = scanControllers();

        for (Map.Entry<String, String> entry : EXEMPT.entrySet()) {
            String simpleName = entry.getKey();
            String reason = entry.getValue();

            List<SourceFile> matches = scanned.stream()
                    .filter(source -> source.simpleName().equals(simpleName))
                    .toList();
            assertThat(matches)
                    .as("陈旧豁免：找不到类 %s（登记理由: %s）—— 请删除该条目", simpleName, reason)
                    .isNotEmpty();

            for (SourceFile source : matches) {
                assertThat(source.hasClassLevelPreAuthorize())
                        .as("陈旧豁免：%s 已经补上类级 @PreAuthorize（登记理由: %s）—— 请删除该条目，"
                                + "否则白名单会膨胀成免责名单", simpleName, reason)
                        .isFalse();
            }
        }
    }

    // ==================================================================================
    // ③ 变异自证：违规样本必须被同一条判定咬住
    // ==================================================================================

    @Test
    @DisplayName("★ 变异自证 — 无注解的 admin 控制器样本必须被判违规；豁免项不误判；注释里提到不算")
    void shouldFlagSyntheticControllerWithoutAnnotation() {
        SourceFile violating = sourceOf("SyntheticAdminController", "/api/admin/synthetic", false);
        SourceFile exempted = sourceOf("SellerFinanceController", "/api/seller/finance", false);
        SourceFile compliant = sourceOf("SyntheticCompliantController", "/api/admin/ok", true);
        SourceFile mentionOnly = sourceOf("SyntheticMentionOnlyController", "/api/admin/mention", false,
                "/** 语义等价于原 @PreAuthorize(\"@ss.hasPermi('user:admin')\") */\n");

        assertThat(isViolation(violating))
                .as("无注解的 /api/admin 控制器必须判违规 —— 否则本门禁是空转")
                .isTrue();
        assertThat(isViolation(compliant)).as("有类级注解的控制器不得误判").isFalse();
        assertThat(isViolation(exempted)).as("豁免项不得判违规（但必须由 ② 管其陈旧性）").isFalse();
        assertThat(isViolation(mentionOnly))
                .as("只在注释里提到 @PreAuthorize 必须仍判违规 —— 扫描前必须先剥注释（实测踩到过）")
                .isTrue();
        assertThat(startsWithGuardedPrefix("/api/order")).as("普通前台端点不归本门禁管").isFalse();
    }

    // ==================================================================================
    // 判定逻辑（抽成可喂合成输入的函数 —— 否则没法自证）
    // ==================================================================================

    /** 单条判定：受管路径 + 未豁免 + 未被允许的注解覆盖 ⇒ 违规。 */
    private static boolean isViolation(SourceFile source) {
        String mapping = source.classMapping();
        if (mapping == null || EXEMPT.containsKey(source.simpleName())) {
            return false;
        }
        if (startsWithAny(mapping, METHOD_LEVEL_ADMIN_PREFIXES)) {
            // 管理端方法级：注解数必须覆盖全部映射方法（无公开端点可豁免）
            if (source.hasClassLevelPreAuthorize()) {
                return false;
            }
            int annotations = source.count("@PreAuthorize");
            int mappings = source.count("@GetMapping", "@PostMapping", "@PutMapping",
                    "@DeleteMapping", "@PatchMapping");
            return mappings > 0 && annotations < mappings;
        }
        if (startsWithAny(mapping, PLATFORM_CONFIG_PREFIXES)) {
            // 平台配置：允许方法级覆盖（类级注解会封掉公开读端点）
            if (source.hasClassLevelPreAuthorize()) {
                return false;
            }
            int annotations = source.count("@PreAuthorize");
            int writes = source.count(WRITE_MAPPINGS);
            return writes > 0 && annotations < writes;
        }
        return startsWithGuardedPrefix(mapping) && !source.hasClassLevelPreAuthorize();
    }

    private static boolean startsWithAny(String mapping, String[] prefixes) {
        for (String prefix : prefixes) {
            if (mapping.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithGuardedPrefix(String mapping) {
        if (mapping == null) {
            return false;
        }
        for (String prefix : GUARDED_PREFIXES) {
            if (mapping.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 合成样本（不上盘：直接把源码片段包成同一个 record 结构）。 */
    private static SourceFile sourceOf(String simpleName, String classMapping, boolean annotated) {
        return sourceOf(simpleName, classMapping, annotated, "");
    }

    private static SourceFile sourceOf(String simpleName, String classMapping, boolean annotated, String javadoc) {
        String header = javadoc
                + (annotated ? "@PreAuthorize(\"@ss.hasPermi('user:admin')\")\n" : "")
                + "@RestController\n@RequestMapping(\"" + classMapping + "\")\n";
        String body = header + "public class " + simpleName + " { }\n";
        return new SourceFile(Paths.get(simpleName + ".java"), simpleName, body);
    }

    private static List<SourceFile> guardedControllers() {
        return scanControllers().stream()
                .filter(source -> startsWithGuardedPrefix(source.classMapping()))
                .toList();
    }

    // ==================================================================================
    // 扫描
    // ==================================================================================

    /**
     * 一个控制器源文件：路径、简单类名、全文。判定用纯文本，不引入解析器。
     *
     * <p><b>必须先剥注释再判定</b>（实测踩到）：{@code KnowledgeBaseController} 的 javadoc 里写着
     * 「语义等价于原 {@code @PreAuthorize}」，朴素的 {@code contains("@PreAuthorize")} 会把它判成
     * 「已有类级注解」—— 一个<b>假阳性</b>。与本仓已记过的「grep 不排除 javadoc」是同一类事故。
     */
    record SourceFile(Path path, String simpleName, String content) {

        /** 剥掉块注释与行注释后的正文（仅用于判定，不改原文件）。 */
        private String cleaned() {
            String noBlockComment = content.replaceAll("(?s)/\\*.*?\\*/", " ");
            return noBlockComment.replaceAll("(?m)//.*$", " ");
        }

        /** 类级 {@code @RequestMapping} 的路径（取 {@code public class} 之前那一处）。 */
        String classMapping() {
            String cleaned = cleaned();
            int classIndex = cleaned.indexOf("public class");
            String header = classIndex > 0 ? cleaned.substring(0, classIndex) : "";
            Matcher matcher = CLASS_MAPPING.matcher(header);
            return matcher.find() ? matcher.group(1) : null;
        }

        /** 类级 {@code @PreAuthorize}：出现在 {@code public class} 之前 ⇒ 覆盖整个控制器。 */
        boolean hasClassLevelPreAuthorize() {
            String cleaned = cleaned();
            int classIndex = cleaned.indexOf("public class");
            String header = classIndex > 0 ? cleaned.substring(0, classIndex) : "";
            return header.contains("@PreAuthorize");
        }

        /** 某标记出现次数（剥注释后计数）。 */
        int count(String... markers) {
            String cleaned = cleaned();
            int total = 0;
            for (String marker : markers) {
                int from = 0;
                while (true) {
                    int index = cleaned.indexOf(marker, from);
                    if (index < 0) {
                        break;
                    }
                    total++;
                    from = index + marker.length();
                }
            }
            return total;
        }

        String relative() {
            return path.toString().replace('\\', '/');
        }
    }

    /** 递归扫描 {@code ../} 下所有 {@code src/main/java/**}{@code *Controller.java}。 */
    private static List<SourceFile> scanControllers() {
        Path backendRoot = Paths.get("..");
        if (!Files.isDirectory(backendRoot)) {
            throw new IllegalStateException("找不到后端根目录: " + backendRoot.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.walk(backendRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .map(AdminEndpointAuthorizationGuardTest::read)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("扫描控制器失败", e);
        }
    }

    private static SourceFile read(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String fileName = path.getFileName().toString();
            return new SourceFile(path, fileName.substring(0, fileName.length() - ".java".length()), content);
        } catch (IOException e) {
            throw new IllegalStateException("读取失败: " + path, e);
        }
    }

    /** 扫描面自检：打印受管控制器清单与豁免条目（排障用）。 */
    @Test
    @DisplayName("扫描面自检 — 打印受管控制器清单与豁免条目")
    void shouldReportGuardSurface() {
        List<String> guarded = List.copyOf(new TreeSet<>(guardedControllers().stream()
                .map(SourceFile::simpleName)
                .toList()));
        System.out.println("[rbac-guard] 受管控制器 = " + guarded.size() + " 个 -> " + guarded);
        System.out.println("[rbac-guard] 豁免条目 = " + EXEMPT.keySet());
        assertThat(guarded).isNotEmpty();
    }
}
