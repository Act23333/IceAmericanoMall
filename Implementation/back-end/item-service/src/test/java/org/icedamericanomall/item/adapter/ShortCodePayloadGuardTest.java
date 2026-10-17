package org.icedamericanomall.item.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守卫 5（ADR-063 §守卫 5 / 契约 §1.9⑧）：<b>短码不得进入任何报文</b>。
 *
 * <p><b>为什么用「扫源码」而不是「反射一个类」</b>：短码会通过<b>新增</b>的 VO/DTO 字段
 * 偷偷进报文 —— 逐个类反射断言只能守住今天存在的类，明天新增一个 {@code SpuCardVO.shortCode}
 * 就绕过去了。这里把命中面钉在<b>全部适配层 response/request 与 ia-api 的 dto 目录</b>上：
 * 任何报文对象声明短码字段即失败，并直接点名文件:行。
 *
 * <p><b>命中面自证</b>：扫到的文件数为 0 即视为守卫失效（不得静默通过）——
 * 与 {@code scripts/check-id-invariants.sh} 的自证纪律一致。
 */
@DisplayName("守卫 5 —— 短码不得进入任何 VO / Req / Feign DTO")
class ShortCodePayloadGuardTest {

    /** 命中面（相对 item-service 模块目录）。 */
    private static final List<String> SCAN_DIRS = List.of(
            "src/main/java/org/icedamericanomall/item/adapter/response",
            "src/main/java/org/icedamericanomall/item/adapter/request",
            "../ia-api/src/main/java/org/icedamericanomall/dto");

    /** 违规判据：字段名/JSON 名里出现短码。 */
    private static final List<String> FORBIDDEN = List.of("shortCode", "short_code", "shortcode");

    @Test
    @DisplayName("适配层 response/request 与 ia-api dto 中不存在 shortCode / short_code 字段")
    void shouldNotDeclareShortCodeInPayloads() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;

        for (String dir : SCAN_DIRS) {
            Path root = Paths.get(dir);
            if (!Files.isDirectory(root)) {
                violations.add("命中面缺失（守卫自身失效）: " + root.toAbsolutePath());
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    scanned++;
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (containsForbidden(lines.get(i))) {
                            violations.add(root.relativize(file) + ":" + (i + 1) + " — " + lines.get(i).strip());
                        }
                    }
                }
            }
        }

        // 自证：命中面必须真的被扫到（目录枚举失效时不得报绿）
        assertTrue(scanned > 5, "命中面文件数异常（" + scanned + "），守卫可能已失效");
        assertEquals(List.of(), violations,
                "短码是承载层别名，进报文即升格为对外标识（ADR-063 §守卫 5 / 契约 §1.9⑧）：\n  - "
                        + String.join("\n  - ", violations));
    }

    private static boolean containsForbidden(String line) {
        String stripped = line.strip();
        // 注释里可以讨论短码（本仓大量 javadoc 解释口径），只查真正的字段/注解声明
        if (stripped.startsWith("*") || stripped.startsWith("//") || stripped.startsWith("/*")) {
            return false;
        }
        for (String token : FORBIDDEN) {
            if (stripped.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
