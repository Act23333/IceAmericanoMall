package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 方法解析器的单测（七种形态）。
 *
 * <p><b>为什么要单独测解析器</b>：`FundTransactionGuardTest` 曾出现**假阴性** ——
 * `PayManager#payByBalance` 有 3 个本地资金写却未被判红，根因是"按正则切方法"在
 * lambda / 匿名类 / switch / 字符串 / 注释 / 多行签名 / 无修饰符 这些形态上切错边界。
 * 门禁的绿只有在解析器可信之后才有意义，故先把解析器当独立单元测死，再接进守卫。
 */
@DisplayName("Java 方法解析器 — 七种形态都必须正确切出方法体")
class JavaMethodScannerTest {

    @Test
    @DisplayName("① 常规方法 + 多行签名 → 仍能切出完整方法体")
    void shouldParseMultiLineSignature() {
        String src = "class A {\n"
                + "    public void m(String a,\n"
                + "                   int b,\n"
                + "                   java.util.List<String> c) {\n"
                + "        x.y(a);\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).extracting(MethodScanner.Method::name).containsExactly("m");
        assertThat(methods.get(0).body()).contains("x.y(a);");
    }

    @Test
    @DisplayName("② 无修饰符（包级私有）方法 → 必须被识别（否则门禁对视同虚设）")
    void shouldParsePackagePrivateMethod() {
        List<MethodScanner.Method> methods = MethodScanner.parse("class B { void pkg() { a.b(); } }");
        assertThat(methods).extracting(MethodScanner.Method::name).containsExactly("pkg");
    }

    @Test
    @DisplayName("③ body 内 lambda 的花括号不得被当方法边界")
    void shouldTolerateLambdaBraces() {
        String src = "class C {\n"
                + "    void m() {\n"
                + "        list.forEach(item -> { if (item != null) { use(item); } });\n"
                + "        after();\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).body()).contains("after();");
    }

    @Test
    @DisplayName("④ body 内匿名类的花括号不得被当方法边界")
    void shouldTolerateAnonymousClassBraces() {
        String src = "class D {\n"
                + "    void m() {\n"
                + "        Runnable r = new Runnable() { public void run() { tick(); } };\n"
                + "        after();\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).extracting(MethodScanner.Method::name).containsExactly("m");
        assertThat(methods.get(0).body()).contains("after();");
    }

    @Test
    @DisplayName("⑤ switch 块的花括号不得被当方法边界")
    void shouldTolerateSwitchBraces() {
        String src = "class E {\n"
                + "    void m(int x) {\n"
                + "        switch (x) { case 1: { a(); break; } default: b(); }\n"
                + "        after();\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).body()).contains("after();");
    }

    @Test
    @DisplayName("⑥ 字符串/字符字面量里的花括号与括号不得影响配对")
    void shouldIgnoreBracesInsideStringAndCharLiterals() {
        String src = "class F {\n"
                + "    void m() {\n"
                + "        String s = \"}{)(\";\n"
                + "        char c = '}';\n"
                + "        after();\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).hasSize(1);
        assertThat(methods.get(0).body()).contains("after();");
    }

    @Test
    @DisplayName("⑦ 注释里的花括号/方法签名不得被当代码")
    void shouldIgnoreComments() {
        String src = "class G {\n"
                + "    // void fake() { }\n"
                + "    /* void fake2() { } */\n"
                + "    void real() {\n"
                + "        // }\n"
                + "        after();\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).extracting(MethodScanner.Method::name).containsExactly("real");
        assertThat(methods.get(0).body()).contains("after();");
    }

    @Test
    @DisplayName("★ 回归 — 控制结构 if/for/while/try 的块不得被当方法")
    void shouldNotTreatControlBlocksAsMethods() {
        String src = "class H {\n"
                + "    void m() {\n"
                + "        if (flag) { a(); }\n"
                + "        for (int i = 0; i < 1; i++) { b(); }\n"
                + "        try { c(); } catch (Exception e) { d(); }\n"
                + "        while (false) { e(); }\n"
                + "    }\n"
                + "}";
        List<MethodScanner.Method> methods = MethodScanner.parse(src);
        assertThat(methods).extracting(MethodScanner.Method::name).containsExactly("m");
    }

    /**
     * 括号/字符串/注释感知的方法解析器（本轮修正假阴性的核心）。
     *
     * <p>算法：逐字符扫描，维护花括号深度与字面量状态；遇到深度 1 处的 `{` 时，
     * 反向跳到匹配的 `)`，取 `(` 前的标识符；若它**不是控制关键字**（if/for/while/switch/catch/
     * synchronized/try）也不是 `->`，则判定为方法开头，body = 到匹配 `}` 为止。
     */
    static final class MethodScanner {

        private static final Set<String> CONTROL_KEYWORDS =
                Set.of("if", "for", "while", "switch", "catch", "synchronized", "try", "else", "do");
        private static final Pattern IDENTIFIER = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$");

        record Method(String name, String body) { }

        static List<Method> parse(String rawSource) {
            String source = stripComments(rawSource);
            List<Method> out = new ArrayList<>();
            int depth = 0;
            int length = source.length();
            for (int i = 0; i < length; i++) {
                char ch = source.charAt(i);
                if (ch == '"' || ch == '\'') {
                    i = skipLiteral(source, i);
                    continue;
                }
                if (ch == '{') {
                    if (depth == 1) {
                        String name = methodNameBefore(source, i);
                        if (name != null) {
                            int end = matchBrace(source, i);
                            if (end > i) {
                                out.add(new Method(name, source.substring(i, end + 1)));
                                i = end;
                                continue;
                            }
                        }
                    }
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
            }
            return out;
        }

        private static String methodNameBefore(String source, int braceIndex) {
            int cursor = braceIndex - 1;
            while (cursor >= 0 && Character.isWhitespace(source.charAt(cursor))) {
                cursor--;
            }
            if (cursor < 0 || source.charAt(cursor) != ')') {
                return null;   // 不是 方法签名 -> { 的形态
            }
            int openParen = matchParenBackwards(source, cursor);
            if (openParen <= 0) {
                return null;
            }
            Matcher matcher = IDENTIFIER.matcher(source.substring(Math.max(0, openParen - 80), openParen));
            if (!matcher.find()) {
                return null;
            }
            String name = matcher.group(1);
            return CONTROL_KEYWORDS.contains(name) ? null : name;
        }

        private static int matchParenBackwards(String source, int closeParen) {
            int depth = 0;
            for (int i = closeParen; i >= 0; i--) {
                char ch = source.charAt(i);
                if (ch == ')') {
                    depth++;
                } else if (ch == '(') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static int matchBrace(String source, int openBrace) {
            int depth = 0;
            for (int i = openBrace; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch == '"' || ch == '\'') {
                    i = skipLiteral(source, i);
                    continue;
                }
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        /** 跳过字面量，返回结束引号的下标（转义字符一并跳过）。 */
        private static int skipLiteral(String source, int start) {
            char quote = source.charAt(start);
            for (int i = start + 1; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch == '\\') {
                    i++;
                } else if (ch == quote) {
                    return i;
                }
            }
            return source.length() - 1;
        }

        static String stripComments(String source) {
            StringBuilder out = new StringBuilder(source.length());
            int i = 0;
            while (i < source.length()) {
                char ch = source.charAt(i);
                if (ch == '"' || ch == '\'') {
                    int end = skipLiteral(source, i);
                    out.append(source, i, Math.min(end + 1, source.length()));
                    i = end + 1;
                    continue;
                }
                if (ch == '/' && i + 1 < source.length()) {
                    char next = source.charAt(i + 1);
                    if (next == '/') {
                        while (i < source.length() && source.charAt(i) != '\n') {
                            i++;
                        }
                        continue;
                    }
                    if (next == '*') {
                        i += 2;
                        while (i + 1 < source.length()
                                && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                            i++;
                        }
                        i += 2;
                        continue;
                    }
                }
                out.append(ch);
                i++;
            }
            return out.toString();
        }
    }
}
