package org.icedamericanomall.arch;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 源码解析的唯一入口（JavaParser）—— 替换此前自写的 {@code MethodScanner}（已删除）。
 *
 * <p><b>为什么必须换工具（不是修 bug）</b>：自写实现用 regex + 花括号计数**近似** Java 语法，
 * 于是要维护一份「无穷的形态清单」：lambda / 匿名类 / switch / 字符串 / 字符字面量 / 文本块 /
 * 注解括号 / 跨行签名 / 泛型尖括号 / record / sealed / 嵌套类型。实测失效三次：
 * {@code PayManager#payByBalance} 的 3 个本地资金写被数成 0（<b>假阴性</b>，门禁因此判绿）、
 * {@code WalletManager#refund} 根本解析不出、注解参数里的圆括号让方法被命名成 {@code Transactional}。
 * 每补一个形态就冒出下一个 —— 成本已超过换工具。
 *
 * <p><b>三条风险锁（照抄自本次决策，不许放宽）</b>：
 * <ol>
 *   <li><b>解析失败必须抛</b>（{@link #parse} 用 {@code orElseThrow}）：返回空集合 = 「扫到 0 个方法」
 *       = 门禁<b>假绿</b>，正是本轮灾难的成因。宁可炸，不可静默为空；</li>
 *   <li><b>语料测试必须覆盖三件事</b>：方法数 + 方法名 + <b>body token</b>
 *       （只断言方法名会被「空壳 body」骗过 —— 本轮已发生）；见 {@code JavaSourceParserCorpusTest}；</li>
 *   <li><b>旧结论全部作废重验</b>：换解析器后，迁移前关于「哪些方法被解析 / 计数多少」的结论
 *       一律重新跑，旧的绿与旧的红都不再有效。</li>
 * </ol>
 *
 * <p><b>TRACE 开关（怎么用 —— 别重复「打了没看到」）</b>：保留 `-Dscanner.trace=true`，但<b>不保留</b>
 * 此前那套「退出时落盘到 `/tmp/scanner-trace.log`」的实现 —— 跨 fork 的落盘观测已被证明不可信
 * （三轮预算、结论互相矛盾）。现在它只把**结构化扫描报告**打到 stdout，作为将来接 artifact 的入口。
 *
 * <pre>
 * ① 推荐（本仓实测有效，且**不破坏 JaCoCo**）：
 *      mvn -o -pl architecture-tests -Dscanner.trace=true test
 *    surefire 3.x 默认把 Maven 的 user properties 提升为 fork 内的 system properties。
 * ② 便携写法（老版本 surefire / 需要真 JVM 参数时）：
 *      mvn -o -pl architecture-tests -DargLine="-Dscanner.trace=true" test
 *    ⚠ 实测：`-DargLine` 会**覆盖** JaCoCo 的 agent 参数（两者同名属性），于是本次运行不再生成
 *    `target/jacoco.exec` —— 要覆盖率就别用它，或显式加 `-Djacoco.skip=true` 把意图写明。
 * ③ 输出在哪：**Maven 控制台**（stdout）。surefire 默认**不**把测试 stdout 写进
 *    `target/surefire-reports/*-output.txt`（那需要 `redirectTestOutputToFile=true`）；
 *    该目录里的 `*.txt` / `TEST-*.xml` 只有用例计数与堆栈，**没有**你打的日志。
 *    所以「打了没看到」通常不是开关没生效，而是找错了地方 —— 直接在控制台搜 `[scanner-trace]`。
 * </pre>
 */
final class JavaSourceParser {

    /** 调试开关（默认关）。打开后每次 {@link #parse} 打一行结构化摘要到 stdout；**不落盘**。 */
    static final boolean TRACE = Boolean.getBoolean("scanner.trace");

    private JavaSourceParser() {
    }

    /** 解析配置：**不做**任何容错放宽（容错会把语法错误变成"少数几个方法"的静默偏差）。 */
    private static JavaParser newParser() {
        return new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    /**
     * 解析源码为 AST。
     *
     * <p><b>严格模式（不许「容错恢复」）</b>：JavaParser 默认会在语法错误处**恢复**并给出一个
     * 部分 AST —— 那不是帮忙，那是把「这个文件没解析干净」变成「少数几个方法消失」：
     * 门禁看到 0 个资金写，于是<b>判绿</b>。这正是本轮要消灭的失效形态，故判据取
     * {@link ParseResult#isSuccessful()}（有任何 problem 即失败），**不是**「拿得到 AST 就算过」。
     *
     * @param source 源码文本
     * @param origin 仅用于报错定位（文件名或 "<synthetic>"）
     * @throws IllegalStateException 解析失败（**禁止**退化为空 AST / 部分 AST —— 那是假绿的源头）
     */
    static CompilationUnit parse(String source, String origin) {
        ParseResult<CompilationUnit> result = newParser().parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            List<String> problems = result.getProblems().stream()
                    .map(Problem::toString)
                    .collect(Collectors.toList());
            throw new IllegalStateException("JavaParser 解析失败（" + origin + "）：" + problems);
        }
        CompilationUnit unit = result.getResult().orElseThrow();
        // TRACE 只做「扫描报告」，不写文件：跨 fork 落盘观测不可信，已废。
        if (TRACE) {
            traceReport(origin, unit);
        }
        return unit;
    }

    /** 解析源码（origin 用 `<synthetic>`）。 */
    static CompilationUnit parse(String source) {
        return parse(source, "<synthetic>");
    }

    /** 解析文件。 */
    static CompilationUnit parse(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
    }

    /**
     * 全部方法声明（含构造器以外的所有 {@link MethodDeclaration}，**包括嵌套类型里的**）。
     *
     * <p>注意与旧实现的语义差异：旧的自写扫描器只在「花括号深度 == 1」处认方法，
     * 因此嵌套类/内部类里的方法**看不见**；JavaParser 会一并返回。这是**更宽**的扫描面，
     * 属修复而非回归（漏扫内部类里的资金写同样是假阴性）。
     */
    static List<MethodDeclaration> methods(CompilationUnit unit) {
        return unit.findAll(MethodDeclaration.class);
    }

    /** 解析文件并返回其中的全部方法声明。 */
    static List<MethodDeclaration> methods(Path file) throws IOException {
        return methods(parse(file));
    }

    /** 方法体文本（无方法体——抽象/接口方法——返回空串）。 */
    static String body(MethodDeclaration method) {
        return method.getBody().map(Node::toString).orElse("");
    }

    /** 方法是否带指定注解（**按名称匹配**，忽略包名与参数）。 */
    static boolean hasAnnotation(MethodDeclaration method, String simpleName) {
        return method.getAnnotationByName(simpleName).isPresent();
    }

    /** 是否声明在「类级注解」下（类/接口声明自带该注解 ⇒ 全部方法视同带注解）。 */
    static boolean typeHasAnnotation(CompilationUnit unit, String simpleName) {
        return unit.getTypes().stream()
                .anyMatch(type -> type.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals(simpleName)));
    }

    // ───────────────────────── 资金写计数（A2：在 AST 上判定，不再扫文本） ─────────────────────────

    /**
     * 方法体内的「资金写」次数（**AST 判定**）。
     *
     * <p>口径：{@link MethodCallExpr} 的 receiver 能被**无歧义地还原成一个仓储名**，且该名字匹配资金命名、
     * 方法名在写动词集合内。三种 receiver 形态与处置：
     * <table border="1">
     *   <caption>receiver 形态与计数</caption>
     *   <tr><th>写法</th><th>AST 里是什么</th><th>计数</th></tr>
     *   <tr><td>{@code repo.deduct(...)}</td><td>{@link NameExpr}</td><td><b>算</b></td></tr>
     *   <tr><td>{@code this.repo.deduct(...)}</td><td>{@link com.github.javaparser.ast.expr.FieldAccessExpr}
     *       （其 scope 是 {@code this}）</td><td><b>算</b>（2026-10-19 补，见下「边界」）</td></tr>
     *   <tr><td>{@code getRepo().insert(...)}</td><td>{@link MethodCallExpr}</td>
     *       <td><b>不算（已登记边界）</b></td></tr>
     * </table>
     *
     * <p>另：实体对象（{@code payOrder.markPaid(...)}）名字不匹配资金仓储 ⇒ 不算；
     * 跨行书写（receiver 与 {@code .动词(} 分处两行）在 AST 里与单行同构 ⇒ 照算（旧实现的痛点之一）。
     *
     * <p><b>已登记边界（不许假装守住）：工厂/链式 receiver 不计。</b>
     * {@code getWalletAccountRepository().credit(...)} 这类写法，receiver 是一个**表达式**而不是名字；
     * 要判它必须做类型推导（{@code getXxxRepository()} 的返回类型是不是资金仓储），已超出「静态名字匹配」。
     * 本轮实测（全仓一万七千余个调用表达式）：这种形态**零命中**，故不引入类型解析，改为两只护栏 ——
     * ① 语料用例钉死行为（{@code shouldCountThisPrefixedFundWrite} / {@code shouldIgnoreFactoryChainedCall}）；
     * ② **全仓体检断言** {@code shouldHaveNoFactoryChainedFundWrites}：一旦真有人这么写就红，
     * 逼出「改用字段名写法」或「给门禁补类型解析」的决策 —— 而不是让它静默漏算。
     *
     * @param method 方法声明
     * @param isFundReceiver 判定「该名字是不是资金仓储」的谓词（由调用方提供，便于单测注入）
     * @param isWriteVerb 判定「该方法名是不是写动词」的谓词（由调用方定义前缀集合，便于单测注入）
     */
    static int countFundWrites(MethodDeclaration method,
                               java.util.function.Predicate<String> isFundReceiver,
                               java.util.function.Predicate<String> isWriteVerb) {
        int count = 0;
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            Optional<String> receiver = call.getScope().flatMap(JavaSourceParser::receiverName);
            if (receiver.isEmpty() || !isFundReceiver.test(receiver.get())) {
                continue;
            }
            if (isWriteVerb.test(call.getNameAsString())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 把调用 receiver 还原成「仓储名」；还原不出（表达式 receiver）时返回空。
     *
     * <p>可还原的两种形态：{@code repo}（{@link NameExpr}）与 {@code this.repo}
     * （{@link com.github.javaparser.ast.expr.FieldAccessExpr} 且其 scope 是 {@code this}）。
     * 后者此前被**静默排除** —— 同一句资金写，写成 {@code this.walletAccountRepository.deduct(...)}
     * 就不计数，属假阴性；本轮补上（全仓实测该形态零命中，故不改变任何既有判定）。
     *
     * <p><b>还原不出的形态（登记，不是遗漏）</b>：{@code getRepo().credit(...)}、
     * {@code someFactory.get().insert(...)}、{@code a.b.repo.insert(...)} —— receiver 是表达式或
     * 更深的字段链，靠名字匹配无法确定类型。这类形态由
     * {@code JavaSourceParserCorpusTest#shouldHaveNoFactoryChainedFundWrites} 做全仓体检。
     */
    static Optional<String> receiverName(com.github.javaparser.ast.expr.Expression scope) {
        if (scope instanceof NameExpr nameExpr) {
            return Optional.of(nameExpr.getNameAsString());
        }
        if (scope instanceof com.github.javaparser.ast.expr.FieldAccessExpr fieldAccess
                && fieldAccess.getScope() instanceof com.github.javaparser.ast.expr.ThisExpr) {
            return Optional.of(fieldAccess.getNameAsString());
        }
        return Optional.empty();
    }

    /** 类名（顶层类型，供报告用）。 */
    static List<String> typeNames(CompilationUnit unit) {
        List<String> names = new ArrayList<>();
        for (TypeDeclaration<?> type : unit.getTypes()) {
            names.add(type.getNameAsString());
        }
        return names;
    }

    /** 是否是接口/抽象方法（无 body 属正常形态，语料断言要放过它们）。 */
    static boolean hasNoBody(MethodDeclaration method) {
        return method.getBody().isEmpty();
    }

    /** 该文件其实是接口（`ClassOrInterfaceDeclaration#isInterface`）。 */
    static boolean isInterfaceFile(CompilationUnit unit) {
        return unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(ClassOrInterfaceDeclaration::isInterface);
    }

    /** 全部后端主源码（不含 architecture-tests 自身）；路径**必须** normalize，理由见调用点注释。 */
    static List<Path> mainSources() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/")
                            || p.toString().contains("\\src\\main\\java\\"))
                    .filter(p -> !p.toString().contains("architecture-tests"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    // ───────────────────────── TRACE：结构化扫描报告（stdout，不落盘） ─────────────────────────

    private static void traceReport(String origin, CompilationUnit unit) {
        List<MethodDeclaration> all = methods(unit);
        String detail = all.stream()
                .map(m -> m.getNameAsString() + "(" + body(m).length() + ")")
                .collect(Collectors.joining(","));
        System.out.println("[scanner-trace] origin=" + origin
                + " types=" + typeNames(unit)
                + " methods=" + all.size()
                + " detail=[" + detail + "]");
    }
}
