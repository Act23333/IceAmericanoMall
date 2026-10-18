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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>RBAC 种子提权守卫</b>（S0-pre）—— 把「哪一行种子能给角色提权」变成构建期机械失败。
 *
 * <p><b>为什么需要（真实的「一步之外」风险，不是洁癖）</b>：售后管理端点的唯一防线是
 * {@code @PreAuthorize("@ss.hasPermi('user:admin')")}，而 {@code hasPermi} 的判定是
 * 「{@code *:*:*} 通配（{@code rbac:admin} 除外）→ 否则精确匹配」。
 * <b>注：{@code ROLE_ADMIN} 角色短路已于 2026-10-18 移除</b>（它让权限位对管理员完全失效），
 * 管理员现在经 {@code *:*:*} 通配获得一切 —— 所以「管理员能做什么」同样由<b>种子</b>决定，
 * 这让下面的守卫更有必要，而不是更没必要。
 * 也就是说：<b>商家能不能读/批别家的售后，完全取决于 {@code sys_role_permission} 里
 * 有没有那一行 {@code (ROLE_SELLER, user:admin)}</b>。
 *
 * <p>而 {@code after_sale} 表<b>没有 seller 维度</b>（只有 {@code order_no} + {@code user_id}），
 * 管理端列表只按状态过滤、写侧（{@code /{id}/review}、{@code /{id}/confirm-return}）只按 id 操作
 * —— 一旦有人为「修 403」把这行种子加上，后果不是"看到别家售后"，而是<b>替别家审批退款</b>，
 * 且代码层没有任何兜底。本类就是拦住这一行的那道闸。
 *
 * <p><b>判定抽成纯函数</b>（{@link #findEscalations}）而不是直接断言真实文件：这样两条变异用例
 * 才能喂合成数据，证明「闸真的会落」——<b>只跑一遍真实仓库全绿，什么也证明不了</b>
 * （它可能是恒为空的空规则）。
 */
@DisplayName("RBAC 种子守卫 — 非 ROLE_ADMIN 角色不得持有超级权限位或 user:admin")
class RbacSeedEscalationGuardTest {

    /** 与 surefire 工作目录（architecture-tests 模块根）一致。 */
    private static final Path INIT_SQL = Paths.get("..", "database", "Initialize.sql");

    /** 超级权限位（一切权限的通配）。 */
    static final String SUPER_PERM = "*:*:*";

    /** 管理端权限位 —— 售后/财务/日志等跨商家写操作都挂在它下面。 */
    static final String USER_ADMIN_PERM = "user:admin";

    /** RBAC 管理位（Q6：只给超管、通配符显式排除、必须由种子显式授予）。 */
    static final String RBAC_ADMIN_PERM = "rbac:admin";

    /** ROLE_ADMIN 的角色 ID（种子 {@code sys_role}）。 */
    static final long ROLE_ADMIN_ID = 4L;

    // ==================================================================================
    // ① 真实扫描：当前种子不得存在提权绑定
    // ==================================================================================

    @Test
    @DisplayName("真实种子 — 除 ROLE_ADMIN 外，任何角色都不得绑定 *:*:* 或 user:admin")
    void shouldKeepSeedFreeOfPrivilegeEscalation() {
        RbacSeed seed = loadSeed();

        // 反空断言：解析失效会让下面的断言恒真（这正是本仓库栽过的坑）
        assertThat(seed.permissionsById())
                .as("没解析到任何 sys_permission —— 正则或文件路径已失效，门禁会静默空转")
                .isNotEmpty();
        assertThat(seed.bindings())
                .as("没解析到任何 sys_role_permission 绑定 —— 门禁会静默空转")
                .isNotEmpty();
        assertThat(seed.permissionCodes()).as("种子必须包含超级权限位 %s", SUPER_PERM).contains(SUPER_PERM);
        assertThat(seed.permissionCodes()).as("种子必须包含 %s", USER_ADMIN_PERM).contains(USER_ADMIN_PERM);

        List<String> violations = findEscalations(seed.permissionsById(), seed.bindings());

        assertThat(violations)
                .as("RBAC 种子出现提权绑定（非 ROLE_ADMIN 角色持有超级权限位或 user:admin）—— "
                        + "这会直接变成跨商家读写售后（含审批退款）的能力，且代码层无兜底。"
                        + "修复方向：新开商家专属端点 + 独立权限位，不要给商家灌管理端权限位")
                .isEmpty();
    }

    @Test
    @DisplayName("真实种子 — ROLE_ADMIN 必须持有超级权限位（否则本守卫的对照侧也不成立）")
    void shouldKeepSuperPermissionBoundToAdminOnly() {
        RbacSeed seed = loadSeed();
        Long superPermId = seed.permissionsById().entrySet().stream()
                .filter(e -> SUPER_PERM.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new AssertionError("种子里找不到超级权限位 " + SUPER_PERM));

        assertThat(seed.bindings())
                .as("ROLE_ADMIN 必须绑定超级权限位（角色 ID %d ↔ 权限 ID %d）", ROLE_ADMIN_ID, superPermId)
                .contains(new Binding(ROLE_ADMIN_ID, superPermId));
    }

    // ==================================================================================
    // ①·二 rbac:admin —— 必须是「种子里的显式授予」，不是运行时的角色特判
    // ==================================================================================

    /**
     * Q6 裁决的落地校验：{@code rbac:admin} 的能力必须<b>来自种子数据</b>，不能来自代码里的
     * 「是管理员就放行」式特判。
     *
     * <p>为什么这条要和权限位拆批一起上：{@code rbac:admin} 是唯一「能改变授权本身」的位。
     * 如果它靠运行时特判获得，那么「谁有权改 RBAC」就不再是可审计的数据，而是埋在代码里的隐式规则 ——
     * 而本仓刚刚才因为这类隐式规则（ROLE_ADMIN 短路）踩过一次。
     *
     * <p>三条断言：
     * <ol>
     *   <li>若种子里有 {@code rbac:admin}，则<b>只有 ROLE_ADMIN 持有它</b>，且 ROLE_ADMIN <b>必须</b>持有；</li>
     *   <li>{@code rbac:admin} 这个字面量<b>不得出现在任何 Java 主源码</b>（唯一例外是
     *       {@code PermissionBits} 的通配排除表 —— 那是"拒绝"规则，不是"授予"规则）；</li>
     *   <li>它也不得被任何 {@code @PreAuthorize} 引用，除非种子已就位（由
     *       {@code PermissionBitReferenceGuardTest} 的硬失败方向兜住）。</li>
     * </ol>
     */
    @Test
    @DisplayName("Q6 — rbac:admin 只能由种子显式授予 ROLE_ADMIN，不得有运行时角色特判")
    void shouldGrantRbacAdminThroughSeedDataOnly() {
        RbacSeed seed = loadSeed();

        // 断言②：字面量不得出现在主源码（唯一例外：PermissionBits 的排除表）
        List<String> javaReferences = scanJavaMainSourcesFor("rbac:admin");
        assertThat(javaReferences)
                .as("rbac:admin 出现在 Java 主源码里（除 PermissionBits 的排除表）。"
                        + "它必须只能来自 sys_role_permission 的显式授予 —— "
                        + "运行时角色特判会让「谁有权改 RBAC」变成埋在代码里的隐式规则")
                .allSatisfy(path -> assertThat(path).endsWith("PermissionBits.java"));
        assertThat(javaReferences)
                .as("扫描面为空：一个字面量都没扫到，说明这条断言是空转（PermissionBits 里本应有一处）")
                .isNotEmpty();

        // 断言①：种子里若已引入该位，则持有者必须恰好是 ROLE_ADMIN
        Long rbacAdminId = seed.permissionsById().entrySet().stream()
                .filter(e -> RBAC_ADMIN_PERM.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (rbacAdminId == null) {
            // 迁移尚未执行：当前合法。位引入后本分支自动失效，下面变成硬断言。
            System.out.println("[rbac-seed][WARN] 种子里还没有 " + RBAC_ADMIN_PERM
                    + " —— 拆位迁移（先种子）尚未执行；引入后本用例会强制「只挂 ROLE_ADMIN」");
            return;
        }

        List<String> violations = findRbacAdminHolderViolations(seed.permissionsById(), seed.bindings());
        assertThat(violations)
                .as("%s 必须由种子显式授予且只能给 ROLE_ADMIN（通配符不覆盖它）："
                        + "不授予 = 管理员也改不了 RBAC；给了别人 = 那个角色可以给自己提权", RBAC_ADMIN_PERM)
                .isEmpty();
    }

    /**
     * 判定 {@code rbac:admin} 的持有关系（纯函数，便于喂合成种子做反向验证）。
     *
     * <p>两条硬规则：
     * <ul>
     *   <li>位存在时，<b>必须</b>授予 ROLE_ADMIN（否则管理员也改不了 RBAC）；</li>
     *   <li><b>只能</b>给 ROLE_ADMIN（任何其他角色持有 = 可以给自己提权）。</li>
     * </ul>
     *
     * @param permissionsById 权限 ID → 编码
     * @param bindings        角色/权限绑定
     * @return 违规描述（空 = 干净）。位不存在时返回空（迁移前合法）。
     */
    static List<String> findRbacAdminHolderViolations(Map<Long, String> permissionsById, List<Binding> bindings) {
        Long rbacAdminId = permissionsById.entrySet().stream()
                .filter(entry -> RBAC_ADMIN_PERM.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (rbacAdminId == null) {
            return List.of();   // 迁移尚未引入该位
        }
        List<Long> holders = bindings.stream()
                .filter(binding -> binding.permissionId() == rbacAdminId)
                .map(Binding::roleId)
                .toList();
        List<String> violations = new ArrayList<>();
        if (!holders.contains(ROLE_ADMIN_ID)) {
            violations.add(String.format("%s 未授予 ROLE_ADMIN（角色 ID %d）—— 通配符不覆盖它，"
                    + "不授予等于管理员也改不了 RBAC", RBAC_ADMIN_PERM, ROLE_ADMIN_ID));
        }
        holders.stream()
                .filter(roleId -> roleId != ROLE_ADMIN_ID)
                .forEach(roleId -> violations.add(String.format(
                        "%s 授予了非 ROLE_ADMIN 角色（role=%d）—— 等于该角色可以给自己提权",
                        RBAC_ADMIN_PERM, roleId)));
        return violations;
    }

    // ==================================================================================
    // ①·三 反向验证：硬断言真的会触发（否则「自动转硬断言」只是口头承诺）
    // ==================================================================================

    @Test
    @DisplayName("★ 反向 A — 种子有 rbac:admin 但持有者含非 ROLE_ADMIN 必须判红")
    void shouldFlagRbacAdminGrantedToNonAdmin_whenSeedInjectsIt() {
        Map<Long, String> perms = Map.of(1L, SUPER_PERM, 21L, RBAC_ADMIN_PERM);
        List<Binding> synthetic = List.of(
                new Binding(ROLE_ADMIN_ID, 1L),
                new Binding(ROLE_ADMIN_ID, 21L),
                new Binding(3L, 21L));   // 商家角色也拿到 rbac:admin

        List<String> violations = findRbacAdminHolderViolations(perms, synthetic);

        assertThat(violations)
                .as("非 ROLE_ADMIN 持有 rbac:admin 必须判红 —— 否则「可以给自己提权」这条只在文档里成立")
                .hasSize(1);
        assertThat(violations.get(0)).contains("role=3").contains(RBAC_ADMIN_PERM);
    }

    @Test
    @DisplayName("★ 反向 B — 种子有 rbac:admin 却没给 ROLE_ADMIN 必须判红（通配符顶不了）")
    void shouldFlagRbacAdminMissingOnAdmin_whenSeedForgetsIt() {
        Map<Long, String> perms = Map.of(1L, SUPER_PERM, 21L, RBAC_ADMIN_PERM);
        List<Binding> synthetic = List.of(new Binding(ROLE_ADMIN_ID, 1L));   // 只有通配符

        List<String> violations = findRbacAdminHolderViolations(perms, synthetic);

        assertThat(violations)
                .as("只持通配符不够 —— 它对 rbac:admin 不生效，种子必须显式授予管理员")
                .hasSize(1);
        assertThat(violations.get(0)).contains("未授予 ROLE_ADMIN");
    }

    @Test
    @DisplayName("反向对照 — 位不存在时零违规（迁移前合法）；只给管理员时零违规")
    void shouldStayClean_whenBitAbsentOrAdminOnly() {
        assertThat(findRbacAdminHolderViolations(Map.of(1L, SUPER_PERM), List.of(new Binding(ROLE_ADMIN_ID, 1L))))
                .as("迁移尚未引入该位时不应判红（否则当前构建就是红的）")
                .isEmpty();
        assertThat(findRbacAdminHolderViolations(
                Map.of(1L, SUPER_PERM, 21L, RBAC_ADMIN_PERM),
                List.of(new Binding(ROLE_ADMIN_ID, 1L), new Binding(ROLE_ADMIN_ID, 21L))))
                .as("合法形态被判红的话，上面两条反向用例什么也证明不了")
                .isEmpty();
    }

    // ==================================================================================
    // ①·四 「扫描源码先剥注释」必须有自己的用例（被咬三次的规则不能只活在对话里）
    // ==================================================================================

    /**
     * 判定源码里是否存在对某字面量的**代码级**引用（不含注释、不含 {@code @PreAuthorize} 注解）。
     *
     * <p><b>剔除粒度是「整个注解节点」而不是「注解词」</b>：必须把 {@code @PreAuthorize("...")}
     * 连同**参数里的字符串**一起剔除。只剔注解词、把参数留下，合法注解仍会被判红 —— 修复等于没做。
     *
     * <p><b>已知局限（不假装守住）</b>：这是**字面量**扫描。抓不到以下形态 ——
     * 字符串拼接（把位名拆成两段相加）、{@code String.format}、从配置/DB 读、反射拼装。
     * 静态扫描对"运行时拼出来的位名"无能为力；要覆盖只能靠运行时断言/审计
     * （{@code setUserRole} 的第二层护栏就是那种兜底），而不是靠本守卫。
     * 本条已登记 open-items，**不允许**因为"守卫绿了"就宣称"没有运行时特判"。
     */
    static boolean containsCodeReference(String source, String literal) {
        String code = stripComments(source);
        String stripped = removePreAuthorizeNodes(code);
        return stripped.contains(literal);
    }

    /** 把每个 {@code @PreAuthorize(...)} 整节点（含参数）从源码里剔除，按括号配对处理嵌套。 */
    private static String removePreAuthorizeNodes(String code) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        int markerLength = "@PreAuthorize".length();
        char backslash = (char) 92;
        while (true) {
            int start = code.indexOf("@PreAuthorize", index);
            if (start < 0) {
                out.append(code, index, code.length());
                return out.toString();
            }
            out.append(code, index, start);
            int open = code.indexOf('(', start);
            if (open < 0) {
                index = start + markerLength;
                continue;
            }
            int depth = 0;
            boolean inString = false;
            int cursor = open;
            for (; cursor < code.length(); cursor++) {
                char ch = code.charAt(cursor);
                if (inString && ch == backslash) {
                    cursor++;   // 跳过转义字符，避免把转义引号当成字符串结束
                    continue;
                }
                if (ch == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) {
                    continue;
                }
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
            }
            index = Math.min(cursor + 1, code.length());
        }
    }

    @Test
    @DisplayName("★ 剔除粒度 — 合法 @PreAuthorize 注解不判红，运行时特判必须判红（含嵌套括号与代码内判权）")
    void shouldDistinguishAnnotationFromRuntimeSpecialCase() {
        String bit = RBAC_ADMIN_PERM;

        assertThat(containsCodeReference("@PreAuthorize(\"@ss.hasPermi('" + bit + "')\")\nvoid m() { }", bit))
                .as("合法注解被判红 = 只剔了注解词、留下了参数（修复没生效）")
                .isFalse();
        assertThat(containsCodeReference(
                "@PreAuthorize(\"@ss.hasAnyPermi('" + bit + "','user:read')\")\nvoid m() { }", bit))
                .as("嵌套括号没配对好，参数会留在代码里 → 合法注解又被判红")
                .isFalse();
        assertThat(containsCodeReference("if (admin) { grant(\"" + bit + "\"); }", bit))
                .as("运行时特判抓不到 = 守卫形同虚设")
                .isTrue();
        assertThat(containsCodeReference("boolean a = UserContext.hasPermission(\"" + bit + "\");", bit))
                .as("代码内判权必须被抓到（这是真实发生过的形态）")
                .isTrue();
        assertThat(containsCodeReference("// 参见 " + bit, bit))
                .as("注释里的提及不算代码引用")
                .isFalse();
    }

    @Test
    @DisplayName("★ 剥离生效 — 注释里的字面量不得被当成代码引用，代码里的必须被识别")
    void shouldStripCommentsBeforeScanning() {
        // 构造两种输入：字面量只出现在注释里 vs 出现在代码里。刻意不用多行字符串，
        // 免得为了"制造一个换行"又引入转义 bug（本轮已经因为转义连红三次）。
        String commentOnly = "/** 语义等价于原 " + RBAC_ADMIN_PERM + " */  // 也不要 " + RBAC_ADMIN_PERM;
        String inCode = "private static final Set<String> EXCLUDED = Set.of(" + RBAC_ADMIN_PERM + ");";
        assertThat(stripComments(commentOnly))
                .as("注释里提到该字面量必须被剥掉 —— 本轮就被这条误判过一次（同族第三次）")
                .doesNotContain(RBAC_ADMIN_PERM);

        assertThat(stripComments(inCode))
                .as("代码里的字面量必须保留 —— 否则守卫会漏掉真正的特判")
                .contains(RBAC_ADMIN_PERM);
    }

    /**
     * 扫 Java 主源码（{@code src/main/java}）<b>代码里</b>出现某字面量的文件路径。
     *
     * <p><b>必须先剥注释</b>：{@code PermissionService} 的注释里就写着「{@code rbac:admin} 排除」，
     * 朴素的 {@code contains} 会把「文档提到」判成「代码特判」——假阳性。
     * 这已是本仓第三次踩同一族坑（前两次：grep 扫 javadoc 里的 {@code @PreAuthorize}、
     * knowledge-base 控制器的 {@code @PreAuthorize} 提及）。
     * <b>凡是"扫描源码找语义"的门禁，一律先剥注释再判定。</b>
     */
    private static List<String> scanJavaMainSourcesFor(String literal) {
        try (java.util.stream.Stream<Path> stream = Files.walk(Paths.get(".."))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace("\\", "/").contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String code = stripComments(Files.readString(path, StandardCharsets.UTF_8));
                            // **注解里的引用是合法的**：本守卫要抓的是"运行时特判"
                            //（`hasPermission("rbac:admin")`、字符串常量、按角色直接授予），
                            // 而不是"某个端点需要该位"。注解引用由 PermissionBitReferenceGuardTest 管。
                            // 不加这一步会把 AdminUserController 的合法注解判成违规 —— 规则过粗也是 bug。
                            return containsCodeReference(code, literal);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(path -> path.toString().replace("\\", "/"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("扫描 Java 主源码失败", e);
        }
    }

    /** 剥掉块注释与行注释（仅用于判定，不改原文件）。 */
    private static String stripComments(String content) {
        String noBlockComment = content.replaceAll("(?s)/\\*.*?\\*/", " ");
        return noBlockComment.replaceAll("(?m)//.*$", " ");
    }

    // ==================================================================================
    // ② 门禁自身的变异测试：喂合成种子，断言闸必定落下
    // ==================================================================================

    @Test
    @DisplayName("★ 变异 A — 给非 ROLE_ADMIN 角色插 *:*:* 必须判红")
    void shouldFlagSuperPermissionGrantedToNonAdmin_whenSeedInjectsIt() {
        Map<Long, String> perms = Map.of(1L, SUPER_PERM, 2L, "ai:chat");
        List<Binding> synthetic = List.of(
                new Binding(ROLE_ADMIN_ID, 1L),   // 正常：管理员持超级权限
                new Binding(3L, 1L));             // ❌ 变异：商家角色也拿到超级权限

        List<String> violations = findEscalations(perms, synthetic);

        assertThat(violations)
                .as("给非 ROLE_ADMIN 角色插 %s 必须被判红（等价于把整个管理端交给商家）", SUPER_PERM)
                .hasSize(1);
        assertThat(violations.get(0)).contains("role=3").contains(SUPER_PERM);
    }

    @Test
    @DisplayName("★ 变异 B — 给商家角色（ROLE_SELLER）插 user:admin 必须判红")
    void shouldFlagUserAdminGrantedToMerchantRole_whenSeedInjectsIt() {
        Map<Long, String> perms = Map.of(1L, SUPER_PERM, 5L, USER_ADMIN_PERM, 6L, "seller:admin");
        List<Binding> synthetic = List.of(
                new Binding(ROLE_ADMIN_ID, 1L),
                new Binding(ROLE_ADMIN_ID, 5L),
                new Binding(3L, 6L),              // 正常：商家只有 seller:admin
                new Binding(3L, 5L));             // ❌ 变异：商家也拿到 user:admin

        List<String> violations = findEscalations(perms, synthetic);

        assertThat(violations)
                .as("给商家角色插 %s 必须被判红 —— 这是「修 403 的顺手做法」，代价是跨商家审批退款",
                        USER_ADMIN_PERM)
                .hasSize(1);
        assertThat(violations.get(0)).contains("role=3").contains(USER_ADMIN_PERM);
    }

    @Test
    @DisplayName("反向对照 — 合法种子（管理员持超级位、商家只持 seller:admin）必须零违规")
    void shouldStayClean_whenSeedHasNoEscalation() {
        Map<Long, String> perms = Map.of(1L, SUPER_PERM, 5L, USER_ADMIN_PERM, 6L, "seller:admin");
        List<Binding> legit = List.of(
                new Binding(ROLE_ADMIN_ID, 1L),
                new Binding(ROLE_ADMIN_ID, 5L),
                new Binding(3L, 6L));

        assertThat(findEscalations(perms, legit))
                .as("合法种子被判红的话，上面两条变异用例什么也证明不了")
                .isEmpty();
    }

    @Test
    @DisplayName("★ 种子解析 — INSERT ... SELECT（按编码取 ID）的绑定必须被解析，不能只认 VALUES 字面量")
    void shouldParseBindingsDeclaredByInsertSelect() {
        String syntheticSql = "INSERT INTO `sys_permission` (`id`, `name`, `code`, `type`, `path`, `sort_order`, `status`) "
                + "VALUES (22, 'RBAC 管理', 'rbac:admin', 3, '/api/admin/roles/**', 25, 1);\n"
                + "INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) "
                + "SELECT 4, id FROM `sys_permission` WHERE `code` = 'rbac:admin';";

        RbacSeed parsed = parseSeed(syntheticSql);

        assertThat(parsed.bindings())
                .as("按编码取 ID 的绑定必须被解析 —— 否则守卫在真实种子上是假阴性："
                        + "判不出「非管理员持有」，还误报「未授予管理员」")
                .containsExactly(new Binding(ROLE_ADMIN_ID, 22L));
    }

    // ==================================================================================
    // 判定（纯函数，无 IO）+ 死数据载体 + 解析
    // ==================================================================================

    /**
     * 判定提权绑定：<b>角色不是 ROLE_ADMIN，却持有超级权限位或 {@code user:admin}</b>。
     *
     * @param permissionsById 权限 ID → 权限编码（来自 {@code sys_permission} 种子）
     * @param bindings        角色/权限绑定（来自 {@code sys_role_permission} 种子）
     * @return 违规描述（空 = 干净）
     */
    static List<String> findEscalations(Map<Long, String> permissionsById, List<Binding> bindings) {
        Set<String> forbidden = Set.of(SUPER_PERM, USER_ADMIN_PERM);
        List<String> violations = new ArrayList<>();
        for (Binding binding : bindings) {
            if (binding.roleId() == ROLE_ADMIN_ID) {
                continue;   // 管理员持有一切权限是设计意图
            }
            String code = permissionsById.get(binding.permissionId());
            if (code != null && forbidden.contains(code)) {
                violations.add(String.format(
                        "role=%d 绑定了 permission_id=%d（%s）—— 非 ROLE_ADMIN 持有该位属提权；"
                                + "请改为新开商家专属端点 + 独立权限位",
                        binding.roleId(), binding.permissionId(), code));
            }
        }
        return violations;
    }

    /** 一条「角色 ↔ 权限」绑定。 */
    record Binding(long roleId, long permissionId) {
    }

    /** 种子解析结果。 */
    record RbacSeed(Map<Long, String> permissionsById, Set<String> permissionCodes, List<Binding> bindings) {
    }

    private static RbacSeed loadSeed() {
        if (!Files.isRegularFile(INIT_SQL)) {
            throw new IllegalStateException("找不到 " + INIT_SQL.toAbsolutePath()
                    + " —— 本测试假定 surefire 工作目录是 architecture-tests 模块根");
        }
        String sql;
        try {
            sql = Files.readString(INIT_SQL, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 Initialize.sql 失败", e);
        }
        return parseSeed(sql);
    }

    /**
     * 从 {@code Initialize.sql} 文本解析 RBAC 种子。
     *
     * @param sql 建库脚本全文
     * @return 权限表 + 绑定表
     */
    static RbacSeed parseSeed(String sql) {
        // sys_permission：**只**在该表的 INSERT 语句内解析 (id, '名称', '编码', ...)。
        // 不能全文件扫——`sys_role` 种子也是 (1, 'ROLE_USER', ...) 的形状，会把权限 ID 覆盖成角色名
        // （第一次实现就是这么错的，真实扫描红、变异用例绿，正好暴露扫描面过宽）。
        Map<Long, String> byId = new LinkedHashMap<>();
        Matcher permStmt = Pattern.compile(
                "INSERT INTO `sys_permission`[^;]*?VALUES(.*?)(?:ON DUPLICATE KEY|;)",
                Pattern.DOTALL).matcher(sql);
        while (permStmt.find()) {
            Matcher perm = Pattern.compile("\\((\\d+)\\s*,\\s*'[^']*'\\s*,\\s*'([^']+)'")
                    .matcher(permStmt.group(1));
            while (perm.find()) {
                byId.put(Long.parseLong(perm.group(1)), perm.group(2));
            }
        }

        // sys_role_permission 有**两种**写法，两种都必须解析：
        //   ① VALUES (roleId, permissionId), ...                             —— 字面量
        //   ② INSERT ... SELECT roleId, id FROM sys_permission WHERE code = 'x' / IN (...) —— 按编码取 ID
        // 只解析 ① 会让守卫在 ② 上变**假阴性**：绑定集为空 ⇒「非管理员持有」判不出来，
        // 反而误报「未授予管理员」。2026-10-18 拆位迁移首次用了 ②，当场把这条守卫打红
        // —— 与「status 过滤只验一条通道」是同族事故：**门禁的解析面必须覆盖种子的全部写法**。
        List<Binding> bindings = new ArrayList<>();

        // ① 字面量 VALUES
        Matcher stmt = Pattern.compile(
                "INSERT INTO `sys_role_permission`[^;]*?VALUES(.*?)(?:ON DUPLICATE KEY|;)",
                Pattern.DOTALL).matcher(sql);
        while (stmt.find()) {
            Matcher pair = Pattern.compile("\\((\\d+)\\s*,\\s*(\\d+)\\)").matcher(stmt.group(1));
            while (pair.find()) {
                bindings.add(new Binding(Long.parseLong(pair.group(1)), Long.parseLong(pair.group(2))));
            }
        }

        // ② INSERT ... SELECT <roleId>, id FROM sys_permission WHERE code = 'x' / IN ('x','y')
        Matcher selectStmt = Pattern.compile(
                "INSERT INTO `sys_role_permission`[^;]*?SELECT\\s+(\\d+)\\s*,\\s*id\\s+FROM\\s+`?sys_permission`?"
                        + "\\s+WHERE\\s+`?code`?\\s+(?:=\\s*'([^']+)'|IN\\s*\\(([^)]*)\\))",
                Pattern.DOTALL).matcher(sql);
        while (selectStmt.find()) {
            long roleId = Long.parseLong(selectStmt.group(1));
            List<String> selectedCodes = new ArrayList<>();
            if (selectStmt.group(2) != null) {
                selectedCodes.add(selectStmt.group(2));
            } else if (selectStmt.group(3) != null) {
                Matcher codeMatcher = Pattern.compile("'([^']+)'").matcher(selectStmt.group(3));
                while (codeMatcher.find()) {
                    selectedCodes.add(codeMatcher.group(1));
                }
            }
            for (String code : selectedCodes) {
                byId.entrySet().stream()
                        .filter(entry -> code.equals(entry.getValue()))
                        .findFirst()
                        .ifPresent(entry -> bindings.add(new Binding(roleId, entry.getKey())));
            }
        }

        Set<String> codes = new LinkedHashSet<>(byId.values());
        return new RbacSeed(byId, codes, bindings);
    }
}
