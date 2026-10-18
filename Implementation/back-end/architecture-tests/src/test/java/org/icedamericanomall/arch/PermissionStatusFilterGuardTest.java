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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>权限装配的 status 过滤守卫</b>（2026-10-18，拆位前实测发现缺陷后补上）。
 *
 * <h2>缺陷是什么（不是风格问题）</h2>
 * 权限位拆分迁移选定的停用机制是 {@code sys_permission.status = 0}（见
 * {@code rbac-permission-matrix-draft.md} §7）。但实测发现：装配用户权限集的两条查询
 * <b>都没有过滤 status</b>：
 * <ul>
 *   <li>{@code selectDirectPermCodesByUserId} —— 只 {@code WHERE up.user_id = ?}</li>
 *   <li>{@code selectRolePermCodesByUserId} —— 只 {@code WHERE ur.user_id = ?}</li>
 * </ul>
 * 后果：{@code status = 0} 是<b>纯装饰</b> —— 停用的位照样进 {@code user:perms:}、照样通过
 * {@code hasPermi}。也就是说「后删旧位」那一步<b>什么都没做</b>，而迁移看起来是成功的。
 *
 * <h2>为什么这条也要门禁</h2>
 * 它和「注解引用的位必须存在」是一对：那条防「注解指向虚空」，这条防<b>「停用形同虚设」</b>。
 * 静态 SQL 写在注解里，运行时才会暴露 —— 只能靠源码级门禁，且本仓已有同类范式（SQL 字面量禁令）。
 *
 * <h2>为什么 {@code selectAllPermissions} 不在管辖内</h2>
 * 它服务 RBAC 管理页（要能看见停用位并重新启用），<b>不参与授权判定</b> —— 判定集只来自上面两条。
 * 把展示查询也过滤掉，会让管理页永远看不到停用的位，等于没法恢复。故按「是否产出判定集」划线，
 * 而不是按「是不是 SELECT sys_permission」划线。
 */
@DisplayName("权限守卫 — 装配判定集的查询必须过滤 sys_permission.status = 1")
class PermissionStatusFilterGuardTest {

    private static final Path MAPPER = Paths.get("..", "authorization-service", "src", "main", "java",
            "org", "icedamericanomall", "auth", "infrastructure", "persistence", "rbac", "PermissionMapper.java");

    /** 产出「权限编码判定集」的查询特征：{@code SELECT p.code}。 */
    private static final Pattern DECISION_SET_QUERY = Pattern.compile("SELECT\\s+p\\.code", Pattern.CASE_INSENSITIVE);

    /** {@code @Select(...)} 的实参（可能由多段字符串拼接）。 */
    private static final Pattern SELECT_ANNOTATION = Pattern.compile("@Select\\((.*?)\\)\\s*\\n", Pattern.DOTALL);

    /** 字符串字面量（把拼接还原成一条 SQL）。 */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    // ==================================================================================
    // ① 真实扫描：判定集查询必须带 status 过滤
    // ==================================================================================

    @Test
    @DisplayName("真实扫描 — 两条装配查询都必须带 p.status = 1，且覆盖率不能为空")
    void shouldRequireStatusFilterOnDecisionSetQueries() {
        List<String> sqls = loadDecisionSetQueries();
        assertThat(sqls)
                .as("一条判定集查询都没解析出来 —— 正则或文件路径失效，本门禁会静默空转")
                .hasSizeGreaterThanOrEqualTo(2);

        List<String> violations = findMissingStatusFilter(sqls);
        assertThat(violations)
                .as("下列查询产出权限判定集但没有过滤 status —— 停用（status=0）会形同虚设，"
                        + "迁移的「后删旧位」这一步实际上什么都没做")
                .isEmpty();
    }

    // ==================================================================================
    // ② 变异自证：不带过滤的样本必须被同一条判定咬住
    // ==================================================================================

    @Test
    @DisplayName("★ 变异自证 — 去掉 status 过滤的样本必须判红；带过滤的不得误判")
    void shouldFlagSyntheticQueryWithoutStatusFilter() {
        String compliant = "SELECT p.code FROM sys_permission p "
                + "INNER JOIN sys_user_permission up ON p.id = up.permission_id "
                + "WHERE up.user_id = #{userId} AND p.status = 1";
        String violating = "SELECT p.code FROM sys_permission p "
                + "INNER JOIN sys_user_permission up ON p.id = up.permission_id "
                + "WHERE up.user_id = #{userId}";
        // status 出现在 SELECT 列表里不算过滤（这是最容易骗过朴素检查的形态）
        String selectListOnly = "SELECT p.code, p.status FROM sys_permission p WHERE p.id = #{userId}";

        assertThat(findMissingStatusFilter(List.of(compliant)))
                .as("带过滤的被判红，说明判定写错了")
                .isEmpty();
        assertThat(findMissingStatusFilter(List.of(violating)))
                .as("去掉过滤必须判红 —— 否则本门禁是空转")
                .hasSize(1);
        assertThat(findMissingStatusFilter(List.of(selectListOnly)))
                .as("status 只出现在 SELECT 列表（没在 WHERE 里）必须仍判红 —— "
                        + "否则「查出来带 status 字段」会被当成「过滤了 status」")
                .hasSize(1);
    }

    // ==================================================================================
    // 判定（纯函数）
    // ==================================================================================

    /**
     * 找出缺少 status 过滤的判定集查询。
     *
     * <p>判定要求 status <b>出现在 WHERE 子句里并以 {@code = 1} 比较</b>：
     * 仅出现在 SELECT 列表不算（那是"带出来"，不是"过滤掉"）。
     */
    static List<String> findMissingStatusFilter(List<String> sqls) {
        List<String> violations = new ArrayList<>();
        for (String sql : sqls) {
            String lower = sql.toLowerCase();
            int whereIndex = lower.indexOf("where");
            boolean filteredInWhere = whereIndex >= 0
                    && lower.substring(whereIndex).matches("(?s).*p\\.status\\s*=\\s*1.*");
            if (!filteredInWhere) {
                violations.add(sql.replaceAll("\\s+", " ").trim());
            }
        }
        return violations;
    }

    // ==================================================================================
    // 解析
    // ==================================================================================

    /** 从 Mapper 源码里取出所有「产出判定集」的 SQL（还原字符串拼接）。 */
    private static List<String> loadDecisionSetQueries() {
        if (!Files.isRegularFile(MAPPER)) {
            throw new IllegalStateException("找不到 " + MAPPER.toAbsolutePath()
                    + " —— 本测试假定 surefire 工作目录是 architecture-tests 模块根");
        }
        String source;
        try {
            source = Files.readString(MAPPER, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 PermissionMapper 失败", e);
        }
        return extractDecisionSetQueries(source);
    }

    /** 从源码文本抽取判定集查询（纯函数，方便喂合成输入）。 */
    static List<String> extractDecisionSetQueries(String source) {
        String cleaned = source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
        List<String> queries = new ArrayList<>();
        Matcher annotation = SELECT_ANNOTATION.matcher(cleaned);
        while (annotation.find()) {
            String argument = annotation.group(1);
            StringBuilder sql = new StringBuilder();
            Matcher literal = STRING_LITERAL.matcher(argument);
            while (literal.find()) {
                sql.append(literal.group(1).replace("\\\"", "\""));
            }
            String assembled = sql.toString();
            if (DECISION_SET_QUERY.matcher(assembled).find()) {
                queries.add(assembled);
            }
        }
        return queries;
    }
}
