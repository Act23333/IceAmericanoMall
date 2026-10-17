package org.icedamericanomall.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * <b>降级工厂的类别判定守卫</b>（ArchUnit）—— 一条规则：
 *
 * <blockquote>
 * 位于 {@code org.icedamericanomall.fallback..}、类名含 {@code Fallback} 的降级工厂，
 * <b>只要它依赖 {@code BizException}（即「会抛错的工厂」），就必须同时依赖
 * {@code org.icedamericanomall.exception.UpstreamFailures}</b>。
 * </blockquote>
 *
 * <p><b>为什么立这条规则</b>：本包 {@code package-info} §2.2 规定「这条 cause 属于
 * CLIENT / RULE / TRANSIENT / BUG」只允许问
 * {@code UpstreamFailures#classify} 一个地方（与 {@code UpstreamErrorDecoder} 同一判据）。
 * 会抛错的工厂若绕过它，就会自己写一份状态码判断 —— 于是解码器说 TRANSIENT、降级工厂说 BUG，
 * 调用方的重试决策被两个结论左右（{@code 09-constraints} §三·八 判据表末行
 * 「解码路径与降级路径<b>同一个类别入口</b>」）。纯返回降级值的工厂
 * 不做类别判定时可以不引用它，故规则只约束「会抛错的」那一半。
 *
 * <h2>两条实现要点（都是「让门禁真的会咬人」的必要条件）</h2>
 * <ol>
 *   <li><b>依赖按「工厂单元」聚合</b>：本包的 {@code throw new BizException(...)} 都写在
 *       {@code create()} 返回的<b>匿名内部类</b>里，字节码落在 {@code XxxFallback$1.class}。
 *       只查外层类的 {@code getDirectDependenciesFromSelf()} 会得到「零个类依赖 BizException」——
 *       规则永远为真（这正是本类 {@link #shouldKeepEveryThrowingFactoryOnSingleCategoryEntryPoint()}
 *       里那条「必须至少有一个工厂触发前置条件」的反空规则要防的形态）。故本类把
 *       <b>工厂自身 + 它的全部 {@code 类名$*} 嵌套/匿名类</b>的依赖合并成一个「单元」再判定。</li>
 *   <li><b>变异验证</b>：{@link #shouldFlagFixtureThatBypassesCategoryEntryPoint()} 用变异样本
 *       {@code org.icedamericanomall.fallback.fixture.BypassFallbackFixture}（会抛
 *       {@code BizException} 但完全不引用 {@code UpstreamFailures}）跑<b>同一条规则</b>，
 *       并断言它<b>必定失败</b>。样本源码见
 *       {@code src/test/resources/org/icedamericanomall/fallback/fixture/BypassFallbackFixture.java}
 *       （为什么不在 {@code src/test/java} 见该文件注释：本模块刻意不依赖业务模块）。</li>
 * </ol>
 *
 * <h2>豁免机制</h2>
 * {@link #EXEMPT} 是「类全名 → 理由」的白名单，<b>当前为空</b>（本包全部会抛错的工厂都已走唯一入口）。
 * 白名单必须「陈旧即失败」：登记项对应的类必须真实存在、且<b>仍然违规</b> —— 否则删豁免
 * 就会被人忘记，白名单会无限膨胀成免责名单。
 */
@DisplayName("架构守卫 — 降级工厂的类别判定必须走唯一入口 UpstreamFailures")
class FallbackCategoryGuardTest {

    /** 降级工厂包（相对本模块目录：ia-api 的编译产物）。 */
    private static final String FALLBACK_PACKAGE = "org.icedamericanomall.fallback";

    /** 会抛错的工厂必须依赖的唯一类别判定入口。 */
    private static final String UPSTREAM_FAILURES = "org.icedamericanomall.exception.UpstreamFailures";

    /** 「会抛错」的判据类型（ia-common 业务异常）。 */
    private static final String BIZ_EXCEPTION = "org.icedamericanomall.common.exception.BizException";

    /** 变异样本的类全名（必须落在 {@link #FALLBACK_PACKAGE} 下，否则规则选不中它）。 */
    private static final String FIXTURE_FQN = "org.icedamericanomall.fallback.fixture.BypassFallbackFixture";
    private static final String FIXTURE_RESOURCE =
            "org/icedamericanomall/fallback/fixture/BypassFallbackFixture.java";

    /**
     * 豁免表（类全名 → 理由）。<b>当前为空</b>；任何新增条目都会在
     * {@link #shouldFailWhenExemptionIsStale()} 里被逐条核对「类存在 + 仍在违规」，陈旧即红。
     */
    private static final Map<String, String> EXEMPT = Map.of();

    private static JavaClasses classes;

    @BeforeAll
    static void importAll() {
        Path path = Paths.get("..", "ia-api", "target", "classes");
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("缺少编译产物: " + path.toAbsolutePath()
                    + " —— 请先在 back-end 根目录执行 mvn -DskipTests install（或从根运行全量 mvn test 由 reactor 保证顺序）");
        }
        classes = new ClassFileImporter().importPath(path);
    }

    // ==================================================================================
    // ① 真实扫描：当前全部降级工厂都合规
    // ==================================================================================

    @Test
    @DisplayName("真实扫描 — 会抛错的降级工厂都依赖 UpstreamFailures（且规则前置条件非空）")
    void shouldKeepEveryThrowingFactoryOnSingleCategoryEntryPoint() {
        // 反空规则（1）：被规则选中的工厂必须存在，否则「包路径写错」会伪装成全绿
        JavaClasses subjects = classes.that(isGuardedFallbackFactory());
        assertThat(subjects)
                .as("规则没有选中任何降级工厂 —— 扫描路径或谓词失效（expected: %s.. 下类名含 Fallback 的类）",
                        FALLBACK_PACKAGE)
                .isNotEmpty();

        // 反空规则（2）：必须至少有一个工厂真的依赖 BizException（即规则的前置条件成立）。
        // 若把依赖只看外层类，这条会失败 —— 见类注释「依赖按工厂单元聚合」。
        List<String> throwingFactories = subjects.stream()
                .filter(javaClass -> unitDependencyTargets(javaClass, classes).contains(BIZ_EXCEPTION))
                .map(JavaClass::getName)
                .sorted()
                .toList();
        System.out.println("[fallback-guard] 收进监控的降级工厂 = " + subjects.size()
                + " 个 / 其中「会抛错」（触发本规则前置条件）= " + throwingFactories.size() + " 个"
                + " -> " + throwingFactories);
        assertThat(throwingFactories)
                .as("没有任何降级工厂被识别为「会抛错」—— 规则前置条件恒假，门禁成了空规则")
                .isNotEmpty();

        ArchRule rule = categoryEntryPointGuard(classes);
        rule.check(classes);
    }

    // ==================================================================================
    // ② 豁免表：必须「陈旧即失败」
    // ==================================================================================

    @Test
    @DisplayName("豁免表 — 每个豁免项必须真实存在且仍然违规，否则「陈旧豁免」判红")
    void shouldFailWhenExemptionIsStale() {
        for (Map.Entry<String, String> entry : EXEMPT.entrySet()) {
            String exemptedName = entry.getKey();
            String reason = entry.getValue();

            assertThat(classes.contain(exemptedName))
                    .as("陈旧豁免：类不存在 → %s（登记理由: %s），请删除该豁免", exemptedName, reason)
                    .isTrue();

            Set<String> dependencies = unitDependencyTargets(classes.get(exemptedName), classes);
            assertThat(dependencies)
                    .as("陈旧豁免：%s 已不再依赖 %s（不再被本规则约束），请删除该豁免（登记理由: %s）",
                            exemptedName, BIZ_EXCEPTION, reason)
                    .contains(BIZ_EXCEPTION);
            assertThat(dependencies)
                    .as("陈旧豁免：%s 已经依赖 %s（已合规），请删除该豁免（登记理由: %s）",
                            exemptedName, UPSTREAM_FAILURES, reason)
                    .doesNotContain(UPSTREAM_FAILURES);
        }
    }

    // ==================================================================================
    // ③ 守卫自身的变异验证：违规样本必须被判红
    // ==================================================================================

    @Test
    @DisplayName("★ 变异验证 — 会抛 BizException 却不引用 UpstreamFailures 的样本必须被判红")
    void shouldFlagFixtureThatBypassesCategoryEntryPoint() {
        JavaClasses fixtureUniverse = importMutationFixture();

        // 样本必须真的被规则选中，否则「断言它失败」会变成对空集合的断言（空验证）
        assertThat(fixtureUniverse.that(isGuardedFallbackFactory()))
                .as("变异样本未被规则选中（包名/类名不符）—— 本条变异验证已失效")
                .isNotEmpty();

        Throwable thrown = catchThrowable(() -> categoryEntryPointGuard(fixtureUniverse).check(fixtureUniverse));

        System.out.println("[fallback-guard][mutation] 变异样本被判定为: "
                + (thrown == null ? "合规（❌ 守卫失效）" : thrown.getMessage()));

        assertThat(thrown)
                .as("守卫必须判红「抛 BizException 却不依赖 UpstreamFailures」的工厂，实际却放过了 —— 规则是空规则")
                .isInstanceOf(AssertionError.class);
        assertThat(thrown.getMessage())
                .as("违规信息里必须点名违规类与缺失的依赖入口，便于值班定位")
                .contains(FIXTURE_FQN)
                .contains(UPSTREAM_FAILURES);
    }

    // ==================================================================================
    // 规则本身
    // ==================================================================================

    /**
     * 规则主体：降级工厂单元「依赖 {@code BizException}」⇒「依赖 {@code UpstreamFailures}」。
     *
     * @param universe 本次判定的类集合（真实扫描用全量 ia-api；变异验证用样本目录）
     */
    private static ArchRule categoryEntryPointGuard(JavaClasses universe) {
        return classes()
                .that(isGuardedFallbackFactory())
                .should(shouldDependOnSingleCategoryEntryPoint(universe))
                .because("凡会抛错的降级工厂，类别判定必须走唯一入口 "
                        + "UpstreamFailures.classify(cause)（本包 package-info §2.2 / 09-constraints §三·八"
                        + "「解码路径与降级路径同一个类别入口」）："
                        + "自己写一份状态码判断，会让「解码器」与「降级工厂」对同一枚故障给出两个结论");
    }

    /** 规则的作用对象：降级工厂（排除嵌套/匿名类与豁免项）。 */
    private static DescribedPredicate<JavaClass> isGuardedFallbackFactory() {
        return describe("降级工厂（" + FALLBACK_PACKAGE + ".. 下类名含 Fallback 的非嵌套类，且不在豁免表内）",
                javaClass -> javaClass.getName().startsWith(FALLBACK_PACKAGE + ".")
                        && javaClass.getSimpleName().contains("Fallback")
                        && !javaClass.isNestedClass()
                        && !EXEMPT.containsKey(javaClass.getName()));
    }

    private static ArchCondition<JavaClass> shouldDependOnSingleCategoryEntryPoint(JavaClasses universe) {
        return new ArchCondition<>("依赖 " + BIZ_EXCEPTION + " 时同时依赖 " + UPSTREAM_FAILURES) {
            @Override
            public void check(JavaClass factory, ConditionEvents events) {
                Set<String> dependencies = unitDependencyTargets(factory, universe);
                if (!dependencies.contains(BIZ_EXCEPTION) || dependencies.contains(UPSTREAM_FAILURES)) {
                    return;
                }
                events.add(SimpleConditionEvent.violated(factory, String.format(
                        "%s 会抛 %s（降级失败出口），却未依赖 %s —— 类别判定绕过了唯一入口"
                                + "（例如自写一份状态码/异常类型判断）；请改用 UpstreamFailures.classify(cause)",
                        factory.getName(),
                        simpleName(BIZ_EXCEPTION),
                        simpleName(UPSTREAM_FAILURES))));
            }
        };
    }

    /**
     * 工厂单元的依赖目标集合：<b>工厂自身 + 其全部 {@code 类名$*} 嵌套/匿名类</b>的依赖合并。
     *
     * <p>聚合是必需的：本包的抛错语句都写在 {@code create()} 返回的匿名内部类里，
     * 字节码落在 {@code XxxFallback$1.class}；只看外层类会得到「零个类依赖 BizException」。
     */
    private static Set<String> unitDependencyTargets(JavaClass factory, JavaClasses universe) {
        Set<String> targets = new LinkedHashSet<>();
        addDependencyTargets(factory, targets);
        String nestedPrefix = factory.getName() + "$";
        for (JavaClass candidate : universe) {
            if (candidate.getName().startsWith(nestedPrefix)) {
                addDependencyTargets(candidate, targets);
            }
        }
        return targets;
    }

    private static void addDependencyTargets(JavaClass javaClass, Set<String> sink) {
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
            sink.add(dependency.getTargetClass().getName());
        }
    }

    private static String simpleName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    // ==================================================================================
    // 变异样本：运行时编译（见 BypassFallbackFixture.java 的「为什么不在 src/test/java」）
    // ==================================================================================

    /**
     * 用 JDK 编译器现场编译变异样本，并连同 ia-common 一起导入（让依赖解析到真实类而不是桩）。
     *
     * @return 只含「样本 + ia-common」的小宇宙，供同一条规则判定
     */
    private static JavaClasses importMutationFixture() {
        Path iaCommonClasses = Paths.get("..", "ia-common", "target", "classes");
        if (!Files.isDirectory(iaCommonClasses)) {
            throw new IllegalStateException("缺少编译产物: " + iaCommonClasses.toAbsolutePath()
                    + " —— 请先在 back-end 根目录执行全量编译（mvn -DskipTests install）");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("当前 JVM 不是 JDK（ToolProvider.getSystemJavaCompiler() == null），"
                    + "无法编译变异样本；请用 JDK 21 运行本模块测试");
        }
        try {
            Path workDir = Files.createTempDirectory("fallback-guard-fixture");
            Path classesDir = Files.createDirectories(workDir.resolve("classes"));
            Path sourceFile = workDir.resolve("src").resolve(FIXTURE_RESOURCE);
            Files.createDirectories(sourceFile.getParent());
            try (InputStream source = FallbackCategoryGuardTest.class.getClassLoader()
                    .getResourceAsStream(FIXTURE_RESOURCE)) {
                if (source == null) {
                    throw new IllegalStateException("缺少变异样本源码资源: " + FIXTURE_RESOURCE
                            + "（应位于 architecture-tests/src/test/resources/ 下）");
                }
                Files.copy(source, sourceFile, StandardCopyOption.REPLACE_EXISTING);
            }
            int exitCode = compiler.run(null, null, null,
                    "-proc:none",
                    "-classpath", iaCommonClasses.toAbsolutePath().toString(),
                    "-d", classesDir.toString(),
                    sourceFile.toString());
            if (exitCode != 0) {
                throw new IllegalStateException("变异样本编译失败（javac exit=" + exitCode + "），见上方 javac 输出");
            }
            return new ClassFileImporter().importPaths(classesDir, iaCommonClasses);
        } catch (IOException e) {
            throw new UncheckedIOException("准备变异样本失败", e);
        }
    }
}
