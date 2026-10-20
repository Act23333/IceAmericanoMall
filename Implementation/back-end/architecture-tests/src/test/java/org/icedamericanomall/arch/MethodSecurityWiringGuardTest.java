package org.icedamericanomall.arch;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「配置 → 行为」的静态断言：权限注解<b>写了</b>不等于<b>会生效</b>。
 *
 * <p><b>为什么需要它</b>：`@PreAuthorize` 是一个字符串表达式，它的正确性依赖三件**代码之外**的事：
 * <ol>
 *   <li>某个配置类上有 {@code @EnableMethodSecurity} —— 没有它，全仓注解都是**装饰**（方法照常执行）；</li>
 *   <li>表达式里的 bean 名（{@code @ss}）真有对应的 bean，且方法名拼对 —— 拼错时 Spring 抛
 *       {@code EL1007E/EL1004E}，表现是**运行期 500**，而单测里（无代理）永远绿；</li>
 *   <li>拼写在全仓一致 —— 有人写 {@code hasPermission} / {@code hasPermi (} / 换 bean 名，
 *       其它人照着抄就会扩散。</li>
 * </ol>
 *
 * <p><b>与 E2 的分工</b>：E2（端到端 403）验的是**行为**（谁能过、谁被拒），但它需要 MySQL/Redis/起服务；
 * 本类验的是**接线**（开没开、连没连、拼写对不对），不需要任何运行态。两层都要，缺一层就是盲区：
 * 只有 E2 会漏掉"某个服务没扫到配置类"，只有本类会漏掉"权限位配错但表达式合法"。
 *
 * <p><b>扫描面纪律</b>：本类走 JavaParser 的**注解节点**，所以注释里的
 * `@PreAuthorize("hasAuthority('order:read')")`（`UserContextAuthenticationFilter` 的说明文字）
 * 天然不会被误判 —— 这是换解析器前必须手写 `stripComments` 的老问题（见 open-items §1.17/§1.18.7）。
 */
@DisplayName("方法安全接线 — @EnableMethodSecurity / @ss bean / 表达式拼写 三层静态断言")
class MethodSecurityWiringGuardTest {

    /** 表达式必须是 `@ss.<方法>('…'[, '…'])`，方法只允许这四个（都在 PermissionService 上）。 */
    private static final Pattern EXPECTED_EXPRESSION =
            Pattern.compile("@ss\\.(hasPermi|hasAnyPermi|hasAllPermi|hasRole)\\(\\s*'[^']+'(\\s*,\\s*'[^']+')*\\)");

    /** 扫描面下限：全仓 @PreAuthorize 的实际数量在数十级；掉到个位数说明扫描失效。 */
    private static final int MIN_PREAUTHORIZE_ANNOTATIONS = 30;

    /** 允许出现在表达式里的方法名（必须与 PermissionService 的实现一一对应，下面会交叉断言）。 */
    private static final List<String> ALLOWED_SS_METHODS =
            List.of("hasPermi", "hasAnyPermi", "hasAllPermi", "hasRole");

    @Test
    @DisplayName("① @EnableMethodSecurity 必须存在（否则全仓 @PreAuthorize 都是装饰）")
    void shouldEnableMethodSecurity() throws IOException {
        List<String> carriers = new ArrayList<>();

        for (Path file : JavaSourceParser.mainSources()) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            boolean hit = unit.findAll(AnnotationExpr.class).stream()
                    .anyMatch(a -> a.getNameAsString().equals("EnableMethodSecurity"));
            if (hit) {
                carriers.add(file.getFileName().toString());
            }
        }

        assertThat(carriers)
                .as("没有任何类标注 @EnableMethodSecurity ⇒ 所有 @PreAuthorize 都不会被求值，"
                        + "权限位等于注释。必须在共享模块（ia-common）的配置类上开启。")
                .isNotEmpty();
    }

    @Test
    @DisplayName("② 表达式里的 bean 名 `ss` 必须真有对应 bean，且方法名在实现里存在")
    void shouldWireSsBeanAndItsMethods() throws IOException {
        Path beanClass = null;
        Set<String> publicMethods = new LinkedHashSet<>();

        for (Path file : JavaSourceParser.mainSources()) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                boolean namedSs = type.getAnnotations().stream().anyMatch(a -> {
                    // `@Service("ss")` is SingleMember; `@Service(value = "ss")` is Normal -- both must work.
                    if (a.isSingleMemberAnnotationExpr()) {
                        var v = a.asSingleMemberAnnotationExpr().getMemberValue();
                        return v.isStringLiteralExpr() && v.asStringLiteralExpr().asString().equals("ss");
                    }
                    if (a.isNormalAnnotationExpr()) {
                        return a.asNormalAnnotationExpr().getPairs().stream().anyMatch(pair ->
                                pair.getNameAsString().equals("value")
                                        && pair.getValue().isStringLiteralExpr()
                                        && pair.getValue().asStringLiteralExpr().asString().equals("ss"));
                    }
                    return false;
                });
                if (namedSs) {
                    beanClass = file;
                    type.getMethods().stream()
                            .filter(MethodDeclaration::isPublic)
                            .forEach(m -> publicMethods.add(m.getNameAsString()));
                }
            }
        }

        assertThat(beanClass)
                .as("找不到名为 `ss` 的 bean（@Service(\"ss\") / @Component(\"ss\")）；"
                        + "而全仓 @PreAuthorize 都写成 `@ss.xxx(...)` ⇒ 表达式会在运行期解析失败（500）")
                .isNotNull();
        assertThat(publicMethods)
                .as("`ss` bean（%s）缺这些方法，但表达式里在用 ⇒ 运行期 EL 求值失败", beanClass.getFileName())
                .containsAll(ALLOWED_SS_METHODS);
    }

    @Test
    @DisplayName("★ ③ 全仓 @PreAuthorize 表达式拼写一致（bean 名 + 方法名；注释不参与）")
    void shouldUseConsistentExpressionSpelling() throws IOException {
        record Hit(String file, int line, String expression) { }
        List<Hit> hits = new ArrayList<>();
        int scanned = 0;

        for (Path file : JavaSourceParser.mainSources()) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
                if (!annotation.getNameAsString().equals("PreAuthorize")) {
                    continue;
                }
                scanned++;
                // NOT annotation.toString(): JavaParser renders attached leading comments too, so the
                // example in a comment (`@PreAuthorize("hasAuthority('order:read')")`) was counted as a real
                // annotation (measured). Take only the string argument node.
                String expression = annotation.isSingleMemberAnnotationExpr()
                        ? annotation.asSingleMemberAnnotationExpr().getMemberValue().toString()
                        : annotation.toString();
                if (expression.length() >= 2 && expression.startsWith("\"")
                        && expression.endsWith("\"")) {
                    expression = expression.substring(1, expression.length() - 1);
                }
                if (!EXPECTED_EXPRESSION.matcher(expression).matches()) {
                    int line = annotation.getBegin().map(p -> p.line).orElse(0);
                    hits.add(new Hit(file.getFileName().toString(), line, expression));
                }
            }
        }

        // 反空断言：注释里的示例不算（JavaParser 只给注解节点），所以这里应当只统计真注解
        assertThat(scanned)
                .as("扫描到的 @PreAuthorize 过少（下限 %s）⇒ 扫描面失效，本断言在空转", MIN_PREAUTHORIZE_ANNOTATIONS)
                .isGreaterThanOrEqualTo(MIN_PREAUTHORIZE_ANNOTATIONS);
        assertThat(hits)
                .as("表达式拼写不一致（必须是 `@ss.hasPermi('xxx')` 这类形态；bean 名只能是 ss，"
                        + "方法名只能是 %s）：%s%n"
                        + "  为什么必须一致：拼错时单测全绿、运行期 500 —— 且下一个人会照着抄。", ALLOWED_SS_METHODS, hits)
                .isEmpty();
    }
}
