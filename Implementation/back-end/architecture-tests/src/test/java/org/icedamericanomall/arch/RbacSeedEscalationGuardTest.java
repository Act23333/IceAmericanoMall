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

        List<Long> holders = seed.bindings().stream()
                .filter(binding -> binding.permissionId() == rbacAdminId)
                .map(Binding::roleId)
                .toList();
        assertThat(holders)
                .as("%s 必须显式授予 ROLE_ADMIN（角色 ID %d）—— 通配符不覆盖它，不授予就等于管理员也改不了 RBAC",
                        RBAC_ADMIN_PERM, ROLE_ADMIN_ID)
                .contains(ROLE_ADMIN_ID);
        assertThat(holders)
                .as("%s 只能给 ROLE_ADMIN —— 其他任何角色持有它都等于「可以给自己提权」", RBAC_ADMIN_PERM)
                .containsOnly(ROLE_ADMIN_ID);
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
                            return code.contains(literal);
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

        // sys_role_permission：只取该表 INSERT 的 VALUES 段，避免把别的表误当绑定
        List<Binding> bindings = new ArrayList<>();
        Matcher stmt = Pattern.compile(
                "INSERT INTO `sys_role_permission`[^;]*?VALUES(.*?)(?:ON DUPLICATE KEY|;)",
                Pattern.DOTALL).matcher(sql);
        while (stmt.find()) {
            Matcher pair = Pattern.compile("\\((\\d+)\\s*,\\s*(\\d+)\\)").matcher(stmt.group(1));
            while (pair.find()) {
                bindings.add(new Binding(Long.parseLong(pair.group(1)), Long.parseLong(pair.group(2))));
            }
        }

        Set<String> codes = new LinkedHashSet<>(byId.values());
        return new RbacSeed(byId, codes, bindings);
    }
}
