package org.icedamericanomall.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两份条件删必须用**同一判据**。
 *
 * <p>逐笔与报文分片的重拉是"要么都删、要么都不动"。两份 SQL 是各写一遍的，
 * 改一处忘另一处就会出现"逐笔删了、原文还在"的半状态 —— 那种不一致不会报错，只会让对账数据静默错位。
 *
 * <p>本守卫把两份 {@code EXISTS} 片段（去掉空白后）逐字比较；不一致即红。
 */
@DisplayName("对账条件删判据一致性（源码级）")
class ReconciliationConditionalDeleteConsistencyGuardTest {

    private static final String ITEM_MAPPER = "ReconciliationItemMapper.java";
    private static final String RAW_MAPPER = "ReconciliationRawMapper.java";

    @Test
    @DisplayName("Item 与 Raw 的 EXISTS 判据必须逐字相同")
    void conditionalDeletesShouldShareSamePredicate() throws IOException {
        List<Path> sources = JavaSourceParser.mainSources();
        String itemPredicate = predicateOf(find(sources, ITEM_MAPPER));
        String rawPredicate = predicateOf(find(sources, RAW_MAPPER));

        assertTrue(!itemPredicate.isEmpty(), "在 " + ITEM_MAPPER + " 里找不到 EXISTS 判据 —— 守卫会空转");
        assertEquals(itemPredicate, rawPredicate,
                "两份条件删的判据不一致：改一处必须改另一处，否则会出现半删状态");
    }

    /** 取 "AND EXISTS (" 到其配对结束的片段，去掉所有空白后返回。 */
    private static String predicateOf(Path file) throws IOException {
        String normalized = String.join("", Files.readAllLines(file)).replace(" ", "");
        int start = normalized.indexOf("ANDEXISTS(");
        if (start < 0) {
            return "";
        }
        int end = normalized.indexOf("))", start);
        return end < 0 ? "" : normalized.substring(start, end + 2);
    }

    private static Path find(List<Path> sources, String fileName) {
        return sources.stream()
                .filter(path -> fileName.equals(path.getFileName().toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 " + fileName + " —— 守卫会空转"));
    }
}
