package org.icedamericanomall.pay.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>认领租约的适用边界</b> —— 把「租约必须覆盖最坏批耗时」钉在「配置 ⟷ 常量」的接缝上。
 *
 * <p><b>为什么需要（真实缺陷）</b>：租约首版 120s，依据是「批 100 条 × 单笔 ~200ms ≈ 20s」。
 * 只看了**平均耗时**，漏掉**降级态**：Feign 的 TimeLimiter 上限是 5s，下游全超时时单批最坏
 * = 100 × 5s = <b>500s</b>。于是 120s 的租约在批处理还没跑完时就过期 —— 另一个执行者会把
 * **仍在处理**的行认领走，认领机制在最需要它的场景下失效。
 *
 * <p><b>为什么必须「跟随 profile」</b>：这个 5s 写在 yml 里，而 yml 可能是**多份**的
 * （`application.yml` + `application-{dev,test,prod}.yml`）。只读基础文件的话，任何一个 profile
 * 覆盖了 `timeout-duration`，测试仍会「绿」——它校验的根本不是真正生效的那份配置。
 * 所以本类**扫描全部 `application*.yml`**，要求：① 每一份里的值都等于代码常量；
 * ② 租约必须大于「批大小 × 其中最大的超时上限」。
 */
@DisplayName("认领租约边界 — 租约必须覆盖「批大小 × Feign 超时上限」，且跟随所有 profile")
class InvoiceLinkCompensationLeaseBoundaryTest {

    /** 与 surefire 工作目录（模块根）一致。 */
    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    private static final Pattern TIMEOUT = Pattern.compile("timeout-duration:\\s*([0-9]+)\\s*s");
    private static final Pattern INTERVAL_MS = Pattern.compile("compensation\\.retry\\.interval-ms:\\s*([0-9]+)");

    @Test
    @DisplayName("租约 > 批大小 × 各 profile 中最大的 Feign 超时 —— 降级态（全部超时）下也不会被提前判过期")
    void shouldCoverWorstCaseBatch_whenEveryCallHitsTimeout() {
        Map<Path, Long> timeouts = timeLimiterTimeoutsByFile();

        assertThat(timeouts)
                .as("没有任何 application*.yml 声明 %s —— 声明式配置缺失时租约推导失去依据", TIMEOUT.pattern())
                .isNotEmpty();

        long maxTimeout = timeouts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        long worstCaseBatch = (long) InvoiceLinkCompensationLeasePolicy.BATCH_LIMIT * maxTimeout;

        assertThat((long) InvoiceLinkCompensationLeasePolicy.CLAIM_LEASE_SECONDS)
                .as("租约(%ds) 必须大于「批大小(%d) × 各 profile 中最大超时上限(%ds) = %ds」的最坏批耗时；"
                                + "否则降级态下租约中途过期，仍在处理的行会被第二个执行者认领走。配置文件实测: %s",
                        InvoiceLinkCompensationLeasePolicy.CLAIM_LEASE_SECONDS,
                        InvoiceLinkCompensationLeasePolicy.BATCH_LIMIT, maxTimeout, worstCaseBatch, timeouts)
                .isGreaterThan(worstCaseBatch);
    }

    @Test
    @DisplayName("常量与**每一份** application*.yml 的 timeout-duration 一致 —— 任何 profile 覆盖它都会红")
    void shouldKeepConstantInSync_whenAnyProfileOverridesTimeout() {
        Map<Path, Long> timeouts = timeLimiterTimeoutsByFile();

        assertThat(timeouts)
                .as("没有扫到任何 timeout-duration —— 扫描路径或正则可能已失效（门禁会静默空转）")
                .isNotEmpty();

        timeouts.forEach((file, seconds) ->
                assertThat(InvoiceLinkCompensationLeasePolicy.FEIGN_CALL_TIMEOUT_SECONDS)
                        .as("代码常量 FEIGN_CALL_TIMEOUT_SECONDS(%d) 与 %s 的 timeout-duration(%ds) 不一致："
                                        + "租约推导会失真。改配置必须同时改常量（并复查 CLAIM_LEASE_SECONDS）",
                                InvoiceLinkCompensationLeasePolicy.FEIGN_CALL_TIMEOUT_SECONDS,
                                file.getFileName(), seconds)
                        .isEqualTo(seconds.intValue()));
    }

    @Test
    @DisplayName("租约严格大于调度间隔 —— 上一轮未跑完时下一轮不会抢走仍在处理的行")
    void shouldExceedScheduleInterval_whenPreviousRoundStillRunning() {
        long intervalSeconds = scheduledIntervalSeconds();

        assertThat((long) InvoiceLinkCompensationLeasePolicy.CLAIM_LEASE_SECONDS)
                .as("租约(%ds) 必须严格大于 @Scheduled 间隔(%ds)，否则每轮都会抢走上一轮仍在处理的行",
                        InvoiceLinkCompensationLeasePolicy.CLAIM_LEASE_SECONDS, intervalSeconds)
                .isGreaterThan(intervalSeconds);
    }

    // ==================================================================================
    // 读配置（唯一副作用面）：扫全部 application*.yml，而不是只读基础文件
    // ==================================================================================

    /** @return 文件 → 该文件声明的 TimeLimiter 超时（秒），仅含有声明的文件 */
    private static Map<Path, Long> timeLimiterTimeoutsByFile() {
        Map<Path, Long> result = new LinkedHashMap<>();
        for (Path yml : allApplicationYmls()) {
            Matcher matcher = TIMEOUT.matcher(read(yml));
            if (matcher.find()) {
                result.put(yml, Long.parseLong(matcher.group(1)));
            }
        }
        return result;
    }

    /** 调度间隔：取任一 profile 的显式配置；都没有则用 Job 上的默认 60000ms。 */
    private static long scheduledIntervalSeconds() {
        for (Path yml : allApplicationYmls()) {
            Matcher matcher = INTERVAL_MS.matcher(read(yml));
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1)) / 1000L;
            }
        }
        return 60L;
    }

    private static java.util.List<Path> allApplicationYmls() {
        if (!Files.isDirectory(RESOURCES)) {
            throw new IllegalStateException("找不到配置目录 " + RESOURCES.toAbsolutePath()
                    + " —— 本测试假定 surefire 工作目录是模块根");
        }
        try (Stream<Path> files = Files.list(RESOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("application") && (name.endsWith(".yml") || name.endsWith(".yaml"));
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("列目录失败: " + RESOURCES.toAbsolutePath(), e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取失败: " + file.toAbsolutePath(), e);
        }
    }
}
