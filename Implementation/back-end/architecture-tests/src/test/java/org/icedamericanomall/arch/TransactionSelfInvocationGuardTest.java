package org.icedamericanomall.arch;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守卫：{@code @Transactional} 方法**不得被同类的非事务方法自调用**。
 *
 * <p><b>为什么需要</b>：Spring 的 {@code @Transactional} 靠代理生效。同一个 Bean 内部
 * {@code this.foo()}（或直接写 {@code foo()}）**绕过代理**，注解形同虚设 ——
 * 单测全绿、线上无事务，是这类缺陷最典型的形态。所以「事务是否生效」不能只检查
 * 「注解在不在」，必须检查**调用形态**。
 *
 * <p><b>规则</b>：若 {@code caller} 与 {@code callee} 在同一个类里，且 {@code callee} 带
 * {@code @Transactional}，则 {@code caller} 也必须带 {@code @Transactional}
 * （外层已有事务时，内层不重复开事务是正确的：REQUIRED 语义由外层承担）。
 * <b>跨 Bean 调用（{@code x.foo()}）不在此限</b> —— 那正是走代理的正确形态。
 *
 * <p><b>扫描面</b>：只看 {@code app/} 与 {@code infrastructure/persistence/} 下的事务宿主
 * （事务注解本就只该出现在这两层）。domain/adapter 不扫，避免把「同名方法」误判。
 *
 * <p>已知局限：反射/字符串拼方法名调用不在覆盖内（那属于设计禁止项，不是遗漏）。
 */
class TransactionSelfInvocationGuardTest {

    private static final int MIN_SCANNED_FILES = 200;

    /**
     * 已知历史债务：{@code 类#调用方->被调方 → 现状与修法}。
     *
     * <p>这两处是<b>本批之前就存在</b>的自调用（营销/商家域，不在本次资金批次内），
     * 放行不等于没问题：它们现在就是「注解在、事务不在」。之所以用 Map 而不是忽略：
     * ① 每条必须写清修法；② 下面有一条断言要求<b>清单里的键在扫描中仍能命中</b> ——
     * 修好之后必须删掉对应条目，否则守卫自己红（清单会自我清算，不会烂在代码里）。
     */
    /**
     * 一条债务：现状 + 修法 + <b>deadline</b>。
     *
     * <p>为什么必须有 deadline：没有期限的债务清单与「自我清算」断言是矛盾的 ——
     * 前者允许永久共存，后者假装会消失。带 deadline 后，**过期即红**（下面有断言），
     * 到期只有两条路：修掉，或走裁决重定期限（改这个常量会出现在 diff 里）。
     */
    record Debt(String reason, java.time.LocalDate deadline) {
    }

    private static final java.util.Map<String, Debt> KNOWN_DEBT = new java.util.LinkedHashMap<>();

    static {
        KNOWN_DEBT.put("CouponClaimManager#smartClaim->claim", new Debt(
                "营销域历史代码。写放大：claim 内写 2 张表（coupon 自增发行量 + user_coupon 插入）⇒ "
                        + "自调用时两者不在同一事务，可能「券数已增、领取记录没落」。修法：把 claim 抽到独立 Bean 后删除本条",
                java.time.LocalDate.of(2026, 11, 10)));
        KNOWN_DEBT.put("ShopManager#updateMyDefaultShop->updateMyShop", new Debt(
                "商家域历史代码。写放大：updateMyShop 只写 1 张表（shop.updateProfile）⇒ 无事务只是注解装饰，"
                        + "影响低。修法：把共用逻辑下沉为私有方法并去掉 @Transactional（它本就不需要事务）后删除本条",
                java.time.LocalDate.of(2026, 12, 31)));
    }


    @Test
    @DisplayName("真实扫描 — app/ 与 persistence/ 下不存在「非事务方法自调用事务方法」")
    void shouldNotSelfInvokeTransactionalMethodFromNonTransactionalCaller() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources().stream()
                .filter(path -> path.toString().replace('\\', '/').contains("/app/")
                        || path.toString().replace('\\', '/').contains("/infrastructure/persistence/"))
                .toList();
        assertThat(sources)
                .as("扫描面异常缩小 ⇒ 守卫在空转")
                .hasSizeGreaterThanOrEqualTo(MIN_SCANNED_FILES);

        List<Violation> problems = new ArrayList<>();
        int txMethodsSeen = 0;
        for (Path file : sources) {
            CompilationUnit unit = JavaSourceParser.parse(file);
            problems.addAll(violationsIn(file.getFileName().toString(), unit));
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                txMethodsSeen += transactionalMethodsOf(type).size();
            }
        }

        java.util.Set<String> hitDebt = new java.util.HashSet<>();
        List<String> unexpected = new ArrayList<>();
        for (Violation violation : problems) {
            if (KNOWN_DEBT.containsKey(violation.key())) {
                hitDebt.add(violation.key());
            } else {
                unexpected.add(violation.message());
            }
        }

        assertThat(unexpected)
                .as("自调用事务方法 ⇒ 事务不生效（代理被绕过）；把循环/复用抽到另一个 Bean，或给调用方也加 @Transactional")
                .isEmpty();
        assertThat(hitDebt)
                .as("已知债务清单自我清算：清单里的键必须仍能命中 —— 修好之后必须删掉对应条目，否则守卫自己红")
                .isEqualTo(KNOWN_DEBT.keySet());

        java.time.LocalDate today = java.time.LocalDate.now();
        List<String> overdue = KNOWN_DEBT.entrySet().stream()
                .filter(entry -> today.isAfter(entry.getValue().deadline()))
                .map(entry -> entry.getKey() + "（deadline " + entry.getValue().deadline() + "）")
                .toList();
        assertThat(overdue)
                .as("债务已过期：要么修掉，要么走裁决把 deadline 往后挪（改常量会出现在 diff 里，这是唯一的留痕）")
                .isEmpty();
        assertThat(txMethodsSeen)
                .as("已知清单：必须扫到事务方法（扫不到说明扫描面选错了）")
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("反例1 — 非事务方法直接调同类事务方法（隐式 this）⇒ 红")
    void shouldFlagImplicitThisCall() {
        CompilationUnit unit = JavaParserUtil.parse("""
                package demo;
                import org.springframework.transaction.annotation.Transactional;
                class Demo {
                    void caller() { callee(); }
                    @Transactional(rollbackFor = Exception.class)
                    void callee() { }
                }
                """);
        assertThat(violationsIn("Demo.java", unit)).isNotEmpty();
    }

    @Test
    @DisplayName("反例2 — 显式 this. 调用同类事务方法 ⇒ 红")
    void shouldFlagExplicitThisCall() {
        CompilationUnit unit = JavaParserUtil.parse("""
                package demo;
                import org.springframework.transaction.annotation.Transactional;
                class Demo {
                    void caller() { this.callee(); }
                    @Transactional(rollbackFor = Exception.class)
                    void callee() { }
                }
                """);
        assertThat(violationsIn("Demo.java", unit)).isNotEmpty();
    }

    @Test
    @DisplayName("反例3 — 调用方用 NOT_SUPPORTED：注解在但不建事务 ⇒ 仍须红（不能只看「有注解」）")
    void shouldFlag_whenCallerSuspendsTransaction() {
        CompilationUnit unit = JavaParserUtil.parse("""
                package demo;
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.transaction.annotation.Propagation;
                class Demo {
                    @Transactional(propagation = Propagation.NOT_SUPPORTED)
                    void caller() { callee(); }
                    @Transactional(rollbackFor = Exception.class)
                    void callee() { }
                }
                """);
        assertThat(violationsIn("Demo.java", unit)).isNotEmpty();
    }

    @Test
    @DisplayName("正例1 — 调用方本身也是事务方法：外层事务已覆盖，放行")
    void shouldPass_whenCallerIsTransactionalToo() {
        CompilationUnit unit = JavaParserUtil.parse("""
                package demo;
                import org.springframework.transaction.annotation.Transactional;
                class Demo {
                    @Transactional(rollbackFor = Exception.class)
                    void caller() { callee(); }
                    @Transactional(rollbackFor = Exception.class)
                    void callee() { }
                }
                """);
        assertThat(violationsIn("Demo.java", unit)).isEmpty();
    }

    @Test
    @DisplayName("正例2 — 调另一个 Bean 的事务方法（走代理）⇒ 放行")
    void shouldPass_whenCallingOtherBean() {
        CompilationUnit unit = JavaParserUtil.parse("""
                package demo;
                import org.springframework.transaction.annotation.Transactional;
                class Demo {
                    private Object other;
                    void caller() { other.helper(); }
                    @Transactional(rollbackFor = Exception.class)
                    void helper() { }
                }
                """);
        assertThat(violationsIn("Demo.java", unit)).isEmpty();
    }

    // ─────────────────────────── 纯函数（可被合成样本直接喂） ───────────────────────────

    /** 一条违规：{@code key} 用于名单匹配，{@code message} 给人看。 */
    record Violation(String key, String message) {
    }

    static List<Violation> violationsIn(String file, CompilationUnit unit) {
        List<Violation> problems = new ArrayList<>();
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            Set<String> transactional = transactionalMethodsOf(type);
            if (transactional.isEmpty()) {
                continue;
            }
            for (MethodCallExpr call : type.findAll(MethodCallExpr.class)) {
                if (!transactional.contains(call.getNameAsString())) {
                    continue;
                }
                // 只看同 Bean：无 scope（隐式 this）或 this.
                boolean sameBean = call.getScope().isEmpty()
                        || call.getScope().filter(ThisExpr.class::isInstance).isPresent();
                if (!sameBean) {
                    continue;
                }
                Optional<MethodDeclaration> caller = call.findAncestor(MethodDeclaration.class);
                if (caller.isEmpty()) {
                    continue;
                }
                boolean callerTransactional = hasEffectiveTransaction(caller.get());
                if (!callerTransactional) {
                    String key = type.getNameAsString() + "#" + caller.get().getNameAsString()
                            + "->" + call.getNameAsString();
                    problems.add(new Violation(key, file + ": `" + key + "` 自调用事务方法 —— "
                            + "代理被绕过，@Transactional 不生效；把调用抽到另一个 Bean，或给调用方也加 @Transactional"));
                }
            }
        }
        return problems;
    }

    /**
     * 调用方是否**真的**有事务，而不是「注解在」。
     *
     * <p>只有 {@code REQUIRED / REQUIRES_NEW / NESTED} 算数；{@code NOT_SUPPORTED / NEVER} 明确不建事务，
     * {@code SUPPORTS / MANDATORY} 取决于调用上下文（静态判不了）—— 后者按<b>不算</b>处理（宁可误报）。
     *
     * <p>已知局限（登记）：{@code SUPPORTS} 在外层有事务时其实是安全的，本守卫仍会红；
     * {@code MANDATORY} 同理。这是**故意保守**：静态分析给不出调用链，误报代价低于漏报。
     */
    static boolean hasEffectiveTransaction(MethodDeclaration method) {
        java.util.Optional<com.github.javaparser.ast.expr.AnnotationExpr> annotation =
                method.getAnnotationByName("Transactional");
        if (annotation.isEmpty()) {
            return false;
        }
        String text = annotation.get().toString().toUpperCase(java.util.Locale.ROOT);
        if (text.contains("NOT_SUPPORTED") || text.contains("NEVER")
                || text.contains("SUPPORTS") || text.contains("MANDATORY")) {
            return false;
        }
        return true;
    }

    private static Set<String> transactionalMethodsOf(ClassOrInterfaceDeclaration type) {
        Set<String> names = new HashSet<>();
        for (MethodDeclaration method : type.getMethods()) {
            if (method.isAnnotationPresent("Transactional")) {
                names.add(method.getNameAsString());
            }
        }
        return names;
    }

    /** 只为合成样本服务的极简解析入口（真实扫描用 {@link JavaSourceParser}）。 */
    private static final class JavaParserUtil {

        private JavaParserUtil() {
        }

        static CompilationUnit parse(String source) {
            return com.github.javaparser.StaticJavaParser.parse(source);
        }
    }

    /** 让 checkstyle 之类的静态检查不抱怨未使用的 import（Node 在真实扫描里被用到）。 */
    @SuppressWarnings("unused")
    private static final Class<Node> NODE_TYPE = Node.class;
}
