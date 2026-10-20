package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账状态码值契约守卫。
 *
 * <p>码值是跨模块契约：SQL 命名参数（#{pendingCode}）、DDL 注释、报表统计都按它取值。
 * architecture-tests 没有 trade-service 依赖，因此这里读源码而不导入类。
 *
 * <p>已做变异验证：把 FETCHED(2, …) 改成 FETCHED(9, …) 后本守卫必须红。
 */
@DisplayName("对账状态码值契约（源码级）")
class ReconciliationStatusContractGuardTest {

    /** 期望的码值：改这里之前先想清楚谁在依赖它。 */
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("PENDING", 1);
        EXPECTED.put("FETCHED", 2);
        EXPECTED.put("MATCHED", 3);
        EXPECTED.put("DIFF", 4);
        EXPECTED.put("RESOLVED", 5);
    }

    @Test
    @DisplayName("ReconciliationStatus 的码值必须与契约一致（调号即红）")
    void statusCodesShouldStayStable() throws IOException {
        Path file = JavaSourceParser.mainSources().stream()
                .filter(path -> "ReconciliationStatus.java".equals(path.getFileName().toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 ReconciliationStatus.java —— 守卫会空转"));

        String text = Files.readString(file);
        for (Map.Entry<String, Integer> entry : EXPECTED.entrySet()) {
            Matcher matcher = Pattern.compile(entry.getKey() + "[(][0-9]+[,]").matcher(text);
            assertTrue(matcher.find(), "枚举里找不到常量 " + entry.getKey());
            String digits = matcher.group().replaceAll("[^0-9]", "");
            assertEquals(entry.getValue(), Integer.valueOf(digits),
                    entry.getKey() + " 的码值被改动：SQL 命名参数/DDL 注释/报表都依赖它");
        }
    }
}
