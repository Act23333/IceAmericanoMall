package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线上字段命名一致性守卫 —— 把「全平台 snake_case」这条决策变成构建期机械检查。
 *
 * <p><b>为什么需要它</b>：2026-10-15 审计发现 15 个服务里 4 个配了
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE}、其余 11 个未配，
 * 导致同一份 DTO 在不同服务上线格式不同；站内信链路因此字段全为 null 而功能失效。
 * 更糟的是**现有 21 个 MockMvc 测试全走 {@code standaloneSetup}（不加载 application.yml）、
 * 且全仓 0 个 {@code @WebMvcTest}** —— 即**没有任何测试覆盖真实报文格式**，
 * 这类缺陷在测试里完全不可见。本类补上这一层守卫。
 *
 * <p><b>规则一（{@link #everyHttpServiceDeclaresSnakeCase()}）</b>：每个对外提供 HTTP 的服务，
 * 其 {@code application.yml} 必须声明 {@code spring.jackson.property-naming-strategy: SNAKE_CASE}。
 *
 * <p><b>规则二（{@link #everyRequestParamUsesSnakeCaseWireName()}）</b>：查询参数不受 Jackson
 * 命名策略管辖 —— 线上名取自 {@code @RequestParam} 的 {@code name}（缺省时回落到 Java 形参名）。
 * 因此每个 {@code @RequestParam} 的「生效线上名」都不得是多词 camelCase，必须由
 * {@code name}/{@code value} 显式钉为 snake_case。缺了这条守卫，「JSON 已 snake、查询串仍 camel」
 * 这种半迁移状态可以长期存在而无任何测试报警。
 *
 * <p>本测试**只读文本**（不引入 YAML / 反射依赖），与 {@link ArchitectureTest} 的语义级规则互补。
 * 归属：ADR（命名统一决策）配套守卫。
 */
@DisplayName("命名守卫 — 全平台线上字段必须统一 snake_case")
class WireNamingConventionTest {

    /** 对外提供 HTTP 的服务模块（相对本模块目录）。新增服务时必须同步维护。 */
    private static final List<String> HTTP_SERVICE_MODULES = List.of(
            "gateway-service",
            "user-service", "authorization-service",
            "cart-service", "pay-service", "trade-service", "item-service",
            "logistics-service", "search-service", "ai-service", "marketing-service",
            "notification-service", "verification-service", "wallet-service", "merchant-service");

    /** 规则二的扫描面：提供 HTTP 端点的服务 + 共享 Feign 客户端模块（ia-api）。 */
    private static final List<String> QUERY_SCAN_MODULES = Stream.concat(
            HTTP_SERVICE_MODULES.stream(), Stream.of("ia-api")).toList();

    /** 匹配 {@code property-naming-strategy: XXX}（行尾允许注释）。 */
    private static final Pattern NAMING_STRATEGY =
            Pattern.compile("property-naming-strategy\\s*:\\s*([A-Za-z_]+)");

    /** {@code @RequestParam} 注解起点。 */
    private static final Pattern REQUEST_PARAM = Pattern.compile("@RequestParam\\b");

    /** 「多词 camelCase」判据：小写字母后紧跟大写字母（单字 snake 名与非驼峰名不受影响）。 */
    private static final Pattern CAMEL_CASE_WORD = Pattern.compile("[a-z][A-Z]");

    /** 注解内显式命名：位置式字面量或 {@code name =}/{@code value =}。 */
    private static final Pattern POSITIONAL_NAME = Pattern.compile("^\\(\"([^\"]*)\"");
    private static final Pattern NAMED_ARG = Pattern.compile("\\b(?:name|value)\\s*=\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("每个 HTTP 服务的 application.yml 都必须显式声明 SNAKE_CASE")
    void everyHttpServiceDeclaresSnakeCase() {
        List<String> problems = new ArrayList<>();

        for (String module : HTTP_SERVICE_MODULES) {
            Path yml = Paths.get("..", module, "src", "main", "resources", "application.yml");
            if (!Files.isRegularFile(yml)) {
                problems.add(module + ": 缺少 application.yml（" + yml.toAbsolutePath() + "）");
                continue;
            }
            String text;
            try {
                text = Files.readString(yml, StandardCharsets.UTF_8);
            } catch (IOException e) {
                problems.add(module + ": 读取失败 " + e.getMessage());
                continue;
            }

            Matcher m = NAMING_STRATEGY.matcher(text);
            if (!m.find()) {
                problems.add(module + ": 未声明 spring.jackson.property-naming-strategy"
                        + "（必须为 SNAKE_CASE；否则本服务线上字段风格与其余服务不一致）");
            } else if (!"SNAKE_CASE".equals(m.group(1))) {
                problems.add(module + ": 命名策略为 " + m.group(1) + "，应为 SNAKE_CASE");
            }
        }

        assertTrue(problems.isEmpty(),
                () -> "线上字段命名不统一（审计结论：全平台必须 snake_case）：\n  - "
                        + String.join("\n  - ", problems));
    }

    @Test
    @DisplayName("@RequestParam 的生效线上名不得为多词 camelCase（须显式 name = snake_case）")
    void everyRequestParamUsesSnakeCaseWireName() {
        List<String> problems = new ArrayList<>();

        for (String module : QUERY_SCAN_MODULES) {
            Path root = Paths.get("..", module, "src", "main", "java");
            if (!Files.isDirectory(root)) {
                problems.add(module + ": 源目录不存在（" + root.toAbsolutePath() + "）");
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (QueryParam param : scanQueryParams(file)) {
                        if (CAMEL_CASE_WORD.matcher(param.wireName()).find()) {
                            problems.add(module + "/" + root.relativize(file).toString().replace('\\', '/')
                                    + ":" + param.line() + " — 线上名 `" + param.wireName()
                                    + "` 为 camelCase（Java 形参 `" + param.javaName()
                                    + (param.explicitName() == null ? "`，注解未显式命名）" : "` 已被显式钉名）")
                                    + "；改为 @RequestParam(name = \"" + toSnakeCase(param.wireName()) + "\")");
                        }
                    }
                }
            } catch (IOException e) {
                problems.add(module + ": 扫描失败 " + e.getMessage());
            }
        }

        assertTrue(problems.isEmpty(),
                () -> "查询参数线上名未统一 snake_case（查询参数不受 Jackson 命名策略管辖，"
                        + "必须由 @RequestParam 的 name 显式钉死）：\n  - "
                        + String.join("\n  - ", problems));
    }

    // ──────────────── 文本扫描（注释/字符串安全） ────────────────

    /** 一处 {@code @RequestParam}：记录其 Java 形参名与显式线格式名。 */
    private record QueryParam(Path file, int line, String javaName, String explicitName) {

        /** 生效线上名：显式 {@code name}/{@code value} 优先，缺省回落到 Java 形参名。 */
        String wireName() {
            return explicitName != null ? explicitName : javaName;
        }
    }

    private static List<QueryParam> scanQueryParams(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<QueryParam> found = new ArrayList<>();
        if (!text.contains("@RequestParam")) {
            return found;
        }
        Matcher m = REQUEST_PARAM.matcher(text);
        while (m.find()) {
            if (inComment(text, m.start())) {
                continue;
            }
            int cursor = m.end();
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            String args = null;
            if (cursor < text.length() && text.charAt(cursor) == '(') {
                int end = matchingParen(text, cursor);
                args = text.substring(cursor, end);
                cursor = end;
            }
            String javaName = parameterNameAfter(text, cursor);
            if (javaName == null) {
                continue;
            }
            found.add(new QueryParam(file, lineOf(text, m.start()), javaName, explicitName(args)));
        }
        return found;
    }

    /** {@code @RequestParam} 是否位于注释内（javadoc 里的 {@code {@code @RequestParam}} 等示例）。 */
    private static boolean inComment(String text, int index) {
        int lastOpen = text.lastIndexOf("/*", index);
        int lastClose = text.lastIndexOf("*/", index);
        if (lastOpen > lastClose) {
            return true;
        }
        int lineStart = text.lastIndexOf('\n', index) + 1;
        return text.substring(lineStart, index).contains("//");
    }

    /** 从 {@code (} 起找配对的 {@code )}（跳过字符串字面量与嵌套括号）。 */
    private static int matchingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    /**
     * 取注解之后该形参的<b>变量名</b>：顶层（{@code ()<>} 之外）首个 {@code ,} 或 {@code )} 之前的
     * 最后一个标识符。兼容类型泛型（{@code List<Long>}）、多重注解（{@code @NotNull @Min(1) Long}）
     * 与 {@code Map<String, Object>}（泛型内的逗号不参与切分）。
     */
    private static String parameterNameAfter(String text, int from) {
        int depth = 0;
        String last = null;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' || c == '>') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (depth == 0 && c == ',') {
                break;
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
                    i++;
                }
                last = text.substring(start, i);
                i--;
            }
        }
        return last;
    }

    private static String explicitName(String args) {
        if (args == null) {
            return null;
        }
        Matcher positional = POSITIONAL_NAME.matcher(args);
        if (positional.find()) {
            return positional.group(1);
        }
        Matcher named = NAMED_ARG.matcher(args);
        return named.find() ? named.group(1) : null;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
