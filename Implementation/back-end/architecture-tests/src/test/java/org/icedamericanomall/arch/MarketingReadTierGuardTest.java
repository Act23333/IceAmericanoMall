package org.icedamericanomall.arch;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3：营销域「只读运营」档位的结构守卫。
 *
 * <p><b>为什么这里不写「mock 断言 403」</b>：`@PreAuthorize` 由 Spring Security 的**代理**求值，
 * 纯 Mockito 单测里被直接 `new` 出来的控制器**没有代理**，断言"调用会抛 403"是一条**假测试**
 * （它只证明你没开方法安全，不证明权限配对了）。而真要跑代理就得起 `@SpringBootTest`，
 * 本仓 marketing-service 至今没有 Spring 上下文测试，且种子/端到端 403 需要 MySQL（E1/E2）。
 *
 * <p>⇒ 本类守**能机械验证的那一层**：注解挂在**哪个方法**上、有没有漏挂、读位有没有被挂到写方法上。
 * 这一层恰好是「接线接错」最常发生的地方（类级 + 方法级 = AND ⇒ 只读运营仍 403，且管理员因 `*:*:*`
 * 通配完全无感，问题会被长期掩盖）。真正的 403 行为验证登记在 E2。
 */
@DisplayName("B3 只读运营档位 — 营销域必须逐方法挂位；读位不得落在写方法上")
class MarketingReadTierGuardTest {

    private static final String BE = "../";

    private static final String TASK_CONTROLLER =
            "marketing-service/src/main/java/org/icedamericanomall/marketing/adapter/admin/AdminTaskController.java";

    /** 营销域管理端控制器（当前全部）。新增时必须同步本清单，否则下面的反向断言覆盖不到它。 */
    private static final List<String> MARKETING_ADMIN_CONTROLLERS = List.of(
            "marketing-service/src/main/java/org/icedamericanomall/marketing/adapter/admin/AdminCouponController.java",
            "marketing-service/src/main/java/org/icedamericanomall/marketing/adapter/admin/AdminFlashController.java",
            "marketing-service/src/main/java/org/icedamericanomall/marketing/adapter/admin/AdminGroupBuyController.java",
            "marketing-service/src/main/java/org/icedamericanomall/marketing/adapter/admin/AdminPromotionController.java",
            TASK_CONTROLLER);

    /** 读操作映射注解（GET）。 */
    private static final List<String> GET_MAPPINGS = List.of("GetMapping");

    /** 写操作映射注解。 */
    private static final List<String> WRITE_MAPPINGS =
            List.of("PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");

    @Test
    @DisplayName("★ 任务定义控制器 — 必须逐方法挂位：GET=marketing:read，写方法=marketing:admin，且不留类级")
    void shouldWireTaskControllerPerMethod() throws IOException {
        Path file = Path.of(BE).toAbsolutePath().normalize().resolve(TASK_CONTROLLER);
        assertThat(file).as("控制器不存在（路径漂移？）：" + TASK_CONTROLLER).exists();

        var unit = JavaSourceParser.parse(file);
        ClassOrInterfaceDeclaration type = unit.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        // ① 类级不得有 @PreAuthorize —— 类级 + 方法级是 AND，会让「只有 marketing:read 的运营」依旧 403
        boolean classLevel = type.getAnnotations().stream()
                .anyMatch(a -> a.getNameAsString().equals("PreAuthorize"));
        assertThat(classLevel)
                .as("类级 @PreAuthorize 会被 Spring 与方法级**同时**要求（AND 而非覆盖）⇒ 只读运营仍 403。"
                        + "本类必须逐方法挂位，类级必须为空。")
                .isFalse();

        // ② 逐个映射方法：读 → marketing:read；写 → marketing:admin；且不许漏挂
        Map<String, String> expected = Map.of(
                "page", "marketing:read",
                "create", "marketing:admin",
                "update", "marketing:admin");

        Map<String, String> actual = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        int mappedMethods = 0;
        for (MethodDeclaration method : JavaSourceParser.methods(unit)) {
            String mapping = mappedAnnotations(method);
            if (mapping == null) {
                continue;
            }
            mappedMethods++;
            String bit = permissionBitOf(method);
            if (bit == null) {
                missing.add(method.getNameAsString() + "(" + mapping + ")");
                continue;
            }
            actual.put(method.getNameAsString(), bit);
        }

        assertThat(mappedMethods)
                .as("一个映射方法都没扫到 ⇒ 扫描面失效（守卫在空转）")
                .isEqualTo(expected.size());
        assertThat(missing)
                .as("这些映射方法没挂 @PreAuthorize（管理端没有公开端点，漏挂等于敞开）")
                .isEmpty();
        assertThat(actual)
                .as("逐方法权限位与预期不符：查询必须是 marketing:read，写方法必须是 marketing:admin")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("★ 反向 — 营销域管理端不许把 marketing:read 挂到任何写方法上")
    void shouldNeverPutReadBitOnWriteMethods() throws IOException {
        Path root = Path.of(BE).toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        int scannedMethods = 0;

        for (String controller : MARKETING_ADMIN_CONTROLLERS) {
            Path file = root.resolve(controller);
            assertThat(file).as("控制器不存在（清单陈旧？）：" + controller).exists();
            for (MethodDeclaration method : JavaSourceParser.methods(file)) {
                if (mappedAnnotations(method) == null) {
                    continue;
                }
                scannedMethods++;
                String bit = permissionBitOf(method);
                boolean isWrite = WRITE_MAPPINGS.stream().anyMatch(method::isAnnotationPresent);
                if (isWrite && "marketing:read".equals(bit)) {
                    violations.add(file.getFileName() + "#" + method.getNameAsString());
                }
            }
        }

        assertThat(scannedMethods)
                .as("扫描面异常：营销域管理端映射方法过少，反向断言在空转")
                .isGreaterThan(5);
        assertThat(violations)
                .as("写方法挂了读位 ⇒ 「只读运营」实际能改：%s", violations)
                .isEmpty();
    }

    /** 该方法上的映射注解名（无映射返回 null）。 */
    private static String mappedAnnotations(MethodDeclaration method) {
        for (String g : GET_MAPPINGS) {
            if (method.isAnnotationPresent(g)) {
                return g;
            }
        }
        for (String w : WRITE_MAPPINGS) {
            if (method.isAnnotationPresent(w)) {
                return w;
            }
        }
        return null;
    }

    /** 方法上 {@code @PreAuthorize("@ss.hasPermi('xxx')")} 里的权限位（无则 null）。 */
    private static String permissionBitOf(MethodDeclaration method) {
        var annotation = method.getAnnotationByName("PreAuthorize").orElse(null);
        if (annotation == null) {
            return null;
        }
        var m = java.util.regex.Pattern.compile("hasPermi\\('([^']+)'\\)")
                .matcher(annotation.toString());
        return m.find() ? m.group(1) : null;
    }
}
