package org.icedamericanomall.common.security;

import java.util.Set;

/**
 * <b>权限位的判定规则（唯一实现）</b> —— {@link PermissionService}（声明式 {@code @PreAuthorize} 用）
 * 与 {@link UserContext}（代码内判定用）<b>都调这里</b>。
 *
 * <h2>为什么必须只有一份</h2>
 * 平台里存在两条判定路径：{@code @ss.hasPermi(...)} 与代码内 {@code UserContext.hasPermission(...)}。
 * 它们曾经各写一份逻辑，于是出现「同一个管理员在两条路径上结论不同」的隐性分叉
 * （{@code UserContext} 那份不认通配符，只是靠种子数据巧合才对得上）。
 * 权限规则一旦复制，迟早会漂 —— 所以规则只写在这里。
 *
 * <h2>三条规则</h2>
 * <ol>
 *   <li><b>fail-closed</b>：权限集合为 {@code null}/空 ⇒ 拒绝；</li>
 *   <li><b>通配符</b>：集合含 {@code *:*:*} ⇒ 通过 —— <b>但</b>被 {@link #WILDCARD_EXCLUDED} 显式排除的位除外；</li>
 *   <li><b>精确匹配</b>：集合含该权限串 ⇒ 通过。</li>
 * </ol>
 *
 * <h2>为什么要有「通配符排除表」（Q6 裁决，2026-10-18）</h2>
 * 「改 RBAC 本身」比「改业务数据」敏感：若 {@code *:*:*} 自动覆盖 {@code rbac:admin}，
 * 那么任何拿到超级位的账号都能改角色、改权限 —— 等于<b>提权路径没有被单独看守</b>。
 * 故 {@code rbac:admin} <b>必须被显式授予</b>，通配符不顶用。
 *
 * <p><b>排除表刻意用「精确位」而不是前缀</b>：按 {@code rbac:} 前缀自动扩会让「新增一个 rbac 子位」
 * 悄悄改变已有账号的授权范围；逐条列出则每次新增都必须显式过一次评审。
 * 新增 rbac 子位时，<b>先决定它是否也要排除，再决定是否加进本表</b> —— 不要默认继承。
 */
public final class PermissionBits {

    /** 超级管理员通配符。 */
    public static final String SUPER_ADMIN_PERM = "*:*:*";

    /**
     * 通配符<b>不覆盖</b>的位（必须显式授予）。
     *
     * <p>当前只有 {@code rbac:admin}（改角色/权限本身）。新增条目要写清「为什么超级位不能自动获得」。
     */
    private static final Set<String> WILDCARD_EXCLUDED = Set.of("rbac:admin");

    private PermissionBits() {
    }

    /**
     * 判定权限集合是否授予了某一位。
     *
     * @param permissions 调用方持有的权限集合（可为 {@code null}）
     * @param required    需要的权限位
     * @return {@code true} 表示授予
     */
    public static boolean grants(Set<String> permissions, String required) {
        if (permissions == null || permissions.isEmpty() || required == null) {
            return false;
        }
        if (permissions.contains(required)) {
            return true;
        }
        return permissions.contains(SUPER_ADMIN_PERM) && !WILDCARD_EXCLUDED.contains(required);
    }

    /** 通配符是否对该位生效（供测试与文档用，避免测试去复制排除表）。 */
    public static boolean wildcardCovers(String required) {
        return required != null && !WILDCARD_EXCLUDED.contains(required);
    }
}
