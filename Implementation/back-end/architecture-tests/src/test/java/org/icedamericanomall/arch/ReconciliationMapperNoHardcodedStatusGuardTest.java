package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账 Mapper 不得硬编码状态值守卫。
 *
 * <p>只查 Reconciliation 前缀的 Mapper：其它 Mapper 里的数字写法属历史代码，本守卫不越界。
 * 新代码必须走命名参数（#{pendingCode} / #{fetchedCode}）。
 *
 * <p>已做变异验证：临时把某处参数写成 1 后本守卫必须红（验后还原）。
 */
@DisplayName("对账 Mapper 不硬编码状态值（源码级）")
class ReconciliationMapperNoHardcodedStatusGuardTest {

    /** status = 1 / status IN (1 这类写法；用字符类避免转义。 */
    private static final Pattern HARDCODED =
            Pattern.compile("(?i)status[ ]*(?:=|in)[ ]*[(]?[ ]*[0-9]");

    @Test
    @DisplayName("Reconciliation*Mapper 的 SQL 不得出现数字状态值")
    void mappersShouldNotHardcodeStatus() throws IOException {
        List<Path> mappers = JavaSourceParser.mainSources().stream()
                .filter(path -> path.getFileName().toString().startsWith("Reconciliation")
                        && path.getFileName().toString().endsWith("Mapper.java"))
                .toList();
        assertTrue(!mappers.isEmpty(), "找不到对账 Mapper —— 守卫会空转");

        List<String> problems = new ArrayList<>();
        for (Path mapper : mappers) {
            Matcher matcher = HARDCODED.matcher(Files.readString(mapper));
            while (matcher.find()) {
                problems.add(mapper.getFileName() + " 出现 " + matcher.group());
            }
        }
        assertTrue(problems.isEmpty(),
                "对账 Mapper 出现硬编码状态值，请改用命名参数 #{pendingCode}/#{fetchedCode}：" + problems);
    }
}
