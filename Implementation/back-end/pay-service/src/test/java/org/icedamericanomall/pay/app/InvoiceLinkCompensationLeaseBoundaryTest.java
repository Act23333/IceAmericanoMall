package org.icedamericanomall.pay.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>认领租约的适用边界</b> —— 把「租约必须覆盖最坏批耗时」这件事钉在配置与常量的接缝上。
 *
 * <p><b>为什么需要这个测试（真实缺陷，不是洁癖）</b>：租约首版取值 120s，依据是「批 100 条 ×
 * 单笔 ~200ms ≈ 20s，留 6 倍余量」。这个估算只看**平均耗时**，漏掉了**降级态**：
 * Feign 的 TimeLimiter 上限是 5s，下游全超时时单批最坏 = 100 × 5s = <b>500s</b>，
 * 于是 120s 的租约在批处理还没跑完时就过期 —— 另一个执行者会把**仍在处理**的行认领走，
 * 认领机制在最需要它的场景下失效。
 *
 * <p>而「5s」这个值写在 {@code application.yml}，与 Java 常量分处两地，**必然漂**。
 * 所以这里直接读 yml 交叉断言：调大 Feign 超时而不调租约，测试就红。
 */
@DisplayName("认领租约边界 — 租约必须覆盖「批大小 × Feign 超时上限」的最坏批耗时")
class InvoiceLinkCompensationLeaseBoundaryTest {

    /** 与 surefire 工作目录（模块根）一致。 */
    private static final Path APPLICATION_YML = Paths.get("src", "main", "resources", "application.yml");

    @Test
    @DisplayName("租约 > 批大小 × TimeLimiter 上限 —— 降级态（全部超时）下也不会被提前判过期")
    void shouldCoverWorstCaseBatch_whenEveryCallHitsTimeout() {
        long feignTimeoutSeconds = readTimeLimiterTimeoutSeconds();

        long worstCaseBatch = (long) InvoiceLinkCompensationRetryManager.BATCH_LIMIT * feignTimeoutSeconds;
        assertThat((long) InvoiceLinkCompensationRetryManager.CLAIM_LEASE_SECONDS)
                .as("租约(%ds) 必须大于「批大小(%d) × Feign 超时上限(%ds) = %ds」的最坏批耗时；"
                                + "否则降级态下租约中途过期，仍在处理的行会被第二个执行者认领走",
                        InvoiceLinkCompensationRetryManager.CLAIM_LEASE_SECONDS,
                        InvoiceLinkCompensationRetryManager.BATCH_LIMIT, feignTimeoutSeconds, worstCaseBatch)
                .isGreaterThan(worstCaseBatch);
    }

    @Test
    @DisplayName("常量与 yml 未漂移 —— 硬编码的 FEIGN_CALL_TIMEOUT_SECONDS 必须等于配置值")
    void shouldKeepConstantInSync_whenYmlTimeoutChanges() {
        long fromYml = readTimeLimiterTimeoutSeconds();

        assertThat(InvoiceLinkCompensationLeasePolicy.FEIGN_CALL_TIMEOUT_SECONDS)
                .as("代码常量 FEIGN_CALL_TIMEOUT_SECONDS 与 application.yml 的 timeout-duration 已漂移："
                                + "改一处必须同时改另一处（Java 侧只用于租约估算）")
                .isEqualTo((int) fromYml);
    }

    @Test
    @DisplayName("租约严格大于调度间隔 —— 上一轮未跑完时下一轮不会抢走仍在处理的行")
    void shouldExceedScheduleInterval_whenPreviousRoundStillRunning() {
        long intervalSeconds = readScheduledIntervalSeconds();

        assertThat((long) InvoiceLinkCompensationRetryManager.CLAIM_LEASE_SECONDS)
                .as("租约(%ds) 必须严格大于 @Scheduled 间隔(%ds)，否则每轮都会抢走上一轮仍在处理的行",
                        InvoiceLinkCompensationRetryManager.CLAIM_LEASE_SECONDS, intervalSeconds)
                .isGreaterThan(intervalSeconds);
    }

    // ==================================================================================
    // 读配置（唯一副作用面）
    // ==================================================================================

    private static String readYml() {
        if (!Files.isRegularFile(APPLICATION_YML)) {
            throw new IllegalStateException("找不到 " + APPLICATION_YML.toAbsolutePath()
                    + " —— 本测试假定 surefire 工作目录是模块根");
        }
        try {
            return Files.readString(APPLICATION_YML, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 application.yml 失败", e);
        }
    }

    /** 解析 {@code timelimiter: ... timeout-duration: 5s}（只认第一个，即 default 配置）。 */
    private static long readTimeLimiterTimeoutSeconds() {
        Matcher matcher = Pattern.compile("timeout-duration:\\s*([0-9]+)\\s*s")
                .matcher(readYml());
        if (!matcher.find()) {
            throw new IllegalStateException("application.yml 里找不到 timelimiter 的 timeout-duration（N s 形式）；"
                    + "若改成毫秒请同步本测试的解析");
        }
        return Long.parseLong(matcher.group(1));
    }

    /** 解析重试任务的 {@code interval-ms}（可能以默认值形式出现在 job 的 @Scheduled 表达式里）。 */
    private static long readScheduledIntervalSeconds() {
        String yml = readYml();
        Matcher matcher = Pattern.compile("compensation\\.retry\\.interval-ms:\\s*([0-9]+)")
                .matcher(yml);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1)) / 1000L;
        }
        // 未在 yml 显式配置时，以 Job 上的默认值 60000ms 为准（见 InvoiceLinkCompensationRetryJob）
        return 60L;
    }
}
