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
 * <b>管理端/商家端端点的鉴权门禁</b>（RBAC0 的第 2 步，2026-10-18）—— 让「新增端点忘了鉴权」变成构建失败。
 *
 * <h2>规则</h2>
 * <blockquote>
 * 类级 {@code @RequestMapping} 路径以 <code>/api/admin</code> 或 <code>/api/seller</code> 开头的控制器，
 * <b>必须带类级 {@code @PreAuthorize}</b>（出现在 {@code public class} 之前），或登记在 {@link #EXEMPT}。
 * </blockquote>
 *
 * <h2>为什么是「类级」而不是「每个方法」</h2>
 * 现阶段的权限粒度就是<b>一个控制器一个权限位</b>（实测：14 个 {@code adapter/admin} 控制器、
 * 48 个端点，全部是类级注解）。类级要求有决定性好处：<b>新增方法自动被覆盖</b>，
 * 不存在「加了个 {@code @GetMapping} 忘了加注解」这种形态。
 * 等权限矩阵细化到方法级时，本规则再收紧（那时才需要逐方法判定）。
 *
 * <h2>为什么这条门禁必须先于「拆权限位」上线</h2>
 * 大规模改注解时，人最容易漏掉的是<b>没有注解的那些</b>（本次实测发现 4 个 {@code /api/seller/**}
 * 控制器共 19 个端点<b>完全没有</b> {@code @PreAuthorize}，靠控制器内 {@code hasRole} 兜）。
 * 没有这条门禁，改完也说不清「是覆盖全了，还是本来就没有」。
 *
 * <h2>豁免表纪律</h2>
 * 每条都要写清<b>为什么现在不能加注解</b>与<b>什么时候必须去掉</b>；且<b>陈旧即失败</b> ——
 * 类已不存在、或已补上注解，都必须从表里删除，否则白名单会无限膨胀成免责名单。
 * <b>表内条目属业务边界裁定</b>（哪些端点该由哪个权限位守），须经人工评审后才行删除/保留。
 *
 * <p>实现是<b>源码扫描</b>（不是字节码）：{@code architecture-tests} 不依赖各业务模块，
 * 扫源码可以跨全部 6 个服务的控制器，且不需要 reactor 先编译它们。
 */
@DisplayName("架构守卫 — /api/admin 与 /api/seller 控制器必须有类级 @PreAuthorize")
class AdminEndpointAuthorizationGuardTest {

    /** 受管路径前缀（管理端、商家端）。 */
    private static final String[] GUARDED_PREFIXES = {"/api/admin", "/api/seller"};

    /** 反空断言下限：受管控制器数量的下限（谓词失效会掉到 0）。 */
    private static final int MIN_GUARDED_CONTROLLERS = 15;

    /** 类级路径的匹配：只取 {@code public class} 之前的注解区。 */
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\(\\s*\"([^\"]+)\"");

    /**
     * 豁免表（简单类名 → 理由 + 截止）。<b>当前 4 条全部是「待权限矩阵裁定」</b>，
     * 不是永久豁免 —— 见 {@code rbac-permission-matrix-draft.md}。
     */
    private static final Map<String, Exemption> EXEMPT = buildExempt();

    /**
     * 一条豁免 = 理由 + <b>替代条件</b>。
     *
     * <p>{@code requireInCodeAdminGuard} 为 {@code true} 时，<b>不许只是"放行"</b>：
     * 必须做到「每个映射方法体都调了 {@code assertAdmin()}」—— 因为这类服务
     * （pay / merchant）<b>没有 spring-security 依赖，{@code @PreAuthorize} 根本不可用</b>，
     * 代码内守卫是唯一手段。豁免因此变成<b>可验证的替代条件</b>，而不是免责。
     */
    record Exemption(String reason, boolean requireInCodeAdminGuard) { }

    private static Map<String, Exemption> buildExempt() {
        Map<String, Exemption> table = new LinkedHashMap<>();
        table.put("SellerFinanceController", new Exemption(
                "待矩阵裁定（截止 2026-11-15）：端点全部由 UserContext 取 sellerId 自证身份，"
                        + "但没有声明式权限位；应归 seller:admin 还是拆 finance:read/finance:write 由人工评审定", false));
        table.put("MerchantCouponController", new Exemption(
                "待矩阵裁定（截止 2026-11-15）：同上，属商家营销位（seller:admin 还是 marketing:admin）", false));
        table.put("KnowledgeBaseController", new Exemption(
                "待矩阵裁定（截止 2026-11-15）：控制器内有显式 UserContext.hasRole(ROLE_SELLER)，"
                        + "但属「代码内判定」不可 grep 审计；应改为声明式", false));
        table.put("MerchantController", new Exemption(
                "待矩阵裁定（截止 2026-11-15）：**同类里有一条必须永久豁免** —— POST /api/seller/apply 是"
                        + "入驻申请，任何登录用户都要能调，不能挂 seller:admin；其余端点属商家自有店铺", false));
        // ★ 这两条不是「待裁定」，而是**能力受限**：pay / merchant 未引 spring-boot-starter-security
        //   （ia-common 里是 optional），@PreAuthorize 编译都过不了。替代条件 = 逐个映射方法都有 assertAdmin()。
        table.put("AdminPayController", new Exemption(
                "能力受限：pay-service 无 spring-security 依赖，@PreAuthorize 不可用；"
                        + "替代条件 = 每个映射方法体都调用 assertAdmin()（本门禁强制校验，非放行）。"
                        + "Q5 决定是否补依赖后转声明式", true));
        table.put("AdminMerchantController", new Exemption(
                "能力受限：merchant-service 无 spring-security 依赖，@PreAuthorize 不可用；"
                        + "替代条件 = 每个映射方法体都调用 assertAdmin()（本门禁强制校验，非放行）。"
                        + "Q5 决定是否补依赖后转声明式", true));
        return Map.copyOf(table);
    }

    // ==================================================================================
    // ① 真实扫描：受管控制器都必须有类级 @PreAuthorize（或已登记豁免）
    // ==================================================================================

    @Test
    @DisplayName("真实扫描 — 受管控制器必须有类级 @PreAuthorize（反空断言 + 豁免陈旧即红）")
    void shouldRequireClassLevelPermission_whenControllerServesAdminOrSellerPaths() {
        List<SourceFile> scanned = scanControllers();
        assertThat(scanned)
                .as("扫描面为空：一个控制器都没扫到，说明路径或谓词失效")
                .isNotEmpty();

        List<SourceFile> guarded = scanned.stream()
                .filter(source -> source.classMapping() != null)
                .filter(source -> startsWithGuardedPrefix(source.classMapping()))
                .toList();
        assertThat(guarded)
                .as("受管的 /api/admin|/api/seller 控制器少于 %d 个 —— 谓词或扫描目录可能失效", MIN_GUARDED_CONTROLLERS)
                .hasSizeGreaterThanOrEqualTo(MIN_GUARDED_CONTROLLERS);

        List<String> violations = new ArrayList<>();
        List<String> inCodeGuardViolations = new ArrayList<>();
        for (SourceFile source : guarded) {
            Exemption exemption = EXEMPT.get(source.simpleName());
            if (exemption != null) {
                // 豁免不是放行：要求「代码内守卫逐方法覆盖」时，必须真的逐个方法都有 assertAdmin()
                if (exemption.requireInCodeAdminGuard()
                        && source.mappingCount() != source.assertAdminCallCount()) {
                    inCodeGuardViolations.add(source.relative()
                            + "（映射方法 " + source.mappingCount()
                            + " 个 / assertAdmin() 调用 " + source.assertAdminCallCount()
                            + " 次）—— 替代条件不成立：每个映射方法都必须调 assertAdmin()");
                }
                continue;
            }
            if (!source.hasClassLevelPreAuthorize()) {
                violations.add(source.relative()
                        + "（映射 " + source.classMapping() + "）缺类级 @PreAuthorize");
            }
        }
        assertThat(inCodeGuardViolations)
                .as("这些控制器因缺少 spring-security 依赖而无法用 @PreAuthorize，豁免的替代条件是"
                        + "「每个映射方法都调 assertAdmin()」—— 现在不成立")
                .isEmpty();
        assertThat(violations)
                .as("下列管理端/商家端控制器没有类级 @PreAuthorize —— 新增端点会静默无鉴权；"
                        + "请补 `@PreAuthorize(\"@ss.hasPermi('…')\")`，或在 AdminEndpointAuthorizationGuardTest.EXEMPT "
                        + "登记并对齐权限矩阵草案")
                .isEmpty();
    }

    // ==================================================================================
    // ② 豁免表必须「陈旧即失败」
    // ==================================================================================

    @Test
    @DisplayName("豁免表 — 每条必须仍存在且仍缺注解（已补注解 / 类已删除 都红）")
    void shouldFailWhenExemptionIsStale() {
        List<SourceFile> scanned = scanControllers();

        for (Map.Entry<String, Exemption> entry : EXEMPT.entrySet()) {
            String simpleName = entry.getKey();
            String reason = entry.getValue().reason();

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
    @DisplayName("★ 变异自证 — 无注解的 admin 控制器样本必须被判违规；豁免项不误判")
    void shouldFlagSyntheticControllerWithoutAnnotation() {
        SourceFile violating = sourceOf("SyntheticAdminController", "/api/admin/synthetic", false);
        SourceFile exempted = sourceOf("SellerFinanceController", "/api/seller/finance", false);
        SourceFile compliant = sourceOf("SyntheticCompliantController", "/api/admin/ok", true);

        assertThat(startsWithGuardedPrefix(violating.classMapping())).isTrue();
        assertThat(isViolation(violating))
                .as("无注解的 /api/admin 控制器必须判违规 —— 否则本门禁是空转")
                .isTrue();
        assertThat(isViolation(compliant)).as("有类级注解的控制器不得误判").isFalse();
        assertThat(isViolation(exempted)).as("豁免项不得判违规（但必须由 ② 管其陈旧性）").isFalse();
        // 非受管路径（普通前台端点）不归本门禁管
        assertThat(startsWithGuardedPrefix("/api/order")).isFalse();
    }

    // ==================================================================================
    // 判定逻辑（抽成可喂合成输入的函数 —— 否则没法自证）
    // ==================================================================================

    /** 单条判定：受管路径 + 未豁免 + 无类级注解 ⇒ 违规。 */
    private static boolean isViolation(SourceFile source) {
        return source.classMapping() != null
                && startsWithGuardedPrefix(source.classMapping())
                && !EXEMPT.containsKey(source.simpleName())
                && !source.hasClassLevelPreAuthorize();
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
        String header = (annotated ? "@PreAuthorize(\"@ss.hasPermi('user:admin')\")\n" : "")
                + "@RestController\n@RequestMapping(\"" + classMapping + "\")\n";
        String body = header + "public class " + simpleName + " { }\n";
        return new SourceFile(Paths.get(simpleName + ".java"), simpleName, body);
    }

    // ==================================================================================
    // 扫描
    // ==================================================================================

    /**
     * 一个控制器源文件：路径、简单类名、全文。判定用纯文本，不引入解析器。
     *
     * <p><b>必须先剥注释再判定</b>（实测踩到）：{@code KnowledgeBaseController} 的 javadoc 里写着
     * 「语义等价于原 {@code @PreAuthorize}」，于是朴素的 {@code contains("@PreAuthorize")}
     * 把它判成「已有类级注解」—— 一个<b>假阳性</b>，会让陈旧检查误报。
     * 与本仓已记过的「grep 不排除 javadoc」是同一类事故：<b>扫描前先剥注释</b>。
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

        /** 映射方法个数（用于「代码内守卫逐方法覆盖」的替代条件）。 */
        int mappingCount() {
            return count("@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping", "@PatchMapping");
        }

        /**
         * {@code assertAdmin();} <b>调用</b>次数（去掉注释后计数）——刻意带分号，
         * 以排除方法声明 {@code private void assertAdmin() {} } 本身。
         */
        int assertAdminCallCount() {
            return count("assertAdmin();");
        }

        private int count(String... markers) {
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

        /** 类级 {@code @PreAuthorize}：出现在 {@code public class} 之前 ⇒ 覆盖整个控制器。 */
        boolean hasClassLevelPreAuthorize() {
            String cleaned = cleaned();
            int classIndex = cleaned.indexOf("public class");
            String header = classIndex > 0 ? cleaned.substring(0, classIndex) : "";
            return header.contains("@PreAuthorize");
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

    /** 供断言消息使用的受管控制器名集合（调试时打印，便于确认扫描面）。 */
    @Test
    @DisplayName("扫描面自检 — 打印受管控制器清单与豁免命中情况")
    void shouldReportGuardSurface() {
        List<String> guarded = List.copyOf(new TreeSet<>(scanControllers().stream()
                .filter(source -> startsWithGuardedPrefix(source.classMapping()))
                .map(SourceFile::simpleName)
                .toList()));
        System.out.println("[rbac-guard] 受管控制器 = " + guarded.size() + " 个 -> " + guarded);
        System.out.println("[rbac-guard] 豁免条目 = " + EXEMPT.keySet());
        EXEMPT.forEach((name, exemption) ->
                System.out.println("[rbac-guard]   " + name + " -> inCodeGuard=" + exemption.requireInCodeAdminGuard()));
        assertThat(guarded).isNotEmpty();
    }
}
