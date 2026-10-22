package org.icedamericanomall.trade.infrastructure.adapter.payout;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.port.WithdrawalPayoutPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Mock 出款通道：让「付款中 → 已打款」这条链在**没有真实渠道**时也能被测出来。
 *
 * <p><b>三条护栏（缺一条这个 mock 就会变成骗人的东西）</b>
 * <ol>
 *   <li><b>自曝身份</b>：所有结果 {@code channelRef} 以 {@code MOCK-} 开头，对账时能与真实流水分开。</li>
 *   <li><b>可注入四态</b>：{@code ACCEPTED / REJECTED / TIMEOUT / ASYNC_CALLBACK}。
 *       只返回「成功」的 mock 会让 {@code PAYING} 的失败分支永远测不到。</li>
 *   <li><b>占位凭据 fail-fast</b>：检测的是<b>占位值</b>（{@code CHANGE_ME}/{@code TODO}/{@code xxxx}/{@code your-}…），
 *       不是空值 —— 空值在 mock 模式下属合法。</li>
 * </ol>
 *
 * <p><b>时间</b>：{@code ASYNC_CALLBACK} 的「回执何时到」由 {@link Clock} 决定，测试注入固定时钟推进即可，
 * <b>不 sleep、不真等</b>。
 *
 * <p><b>幂等</b>：同一个 {@code payoutNo} 的<b>终态</b>结果会被冻结并原样返回；
 * 非终态（等回执）每次按时钟重新判定 —— 这正是真实渠道的样子：回执没到之前，每次问都是「还没到」。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "trade.withdrawal.payout-mock.enabled", havingValue = "true")
public class MockPayoutAdapter implements WithdrawalPayoutPort {

    /** mock 通道的渠道号前缀：对账时据此排除在「真实流水核对」之外。 */
    public static final String CHANNEL_PREFIX = "MOCK-";

    /** 占位凭据的识别式（只看形状，不看具体厂商）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i).*(change[_-]?me|todo|xxx+|your[-_ ]|placeholder|示例|待填).*");

    private static final List<String> CREDENTIAL_KEYS = List.of("app-id", "mch-id", "api-key");

    /** mock 行为：四态之一。 */
    public enum Mode {
        /** 受理并立即成功（回执齐全）。 */
        ACCEPTED,
        /** 明确拒绝：调用方**不得**标已打款，实际会停在付款中并告警。 */
        REJECTED,
        /** 结果未知（超时）：同样停在付款中 —— 「未知」不是「失败」。 */
        TIMEOUT,
        /** 异步回执：时钟走过 {@code callbackDelay} 之前一直是「还没到」。 */
        ASYNC_CALLBACK
    }

    private final Mode mode;
    private final Duration callbackDelay;
    private final Clock clock;
    private final Map<String, String> credentials;

    /** 幂等缓存：payoutNo → **终态**结果（非终态不缓存）。 */
    private final Map<String, PayoutResult> terminal = new ConcurrentHashMap<>();

    /** 异步回执的受理时刻（payoutNo → acceptedAt）。 */
    private final Map<String, Instant> acceptedAt = new ConcurrentHashMap<>();

    public MockPayoutAdapter(@Value("${trade.withdrawal.payout-mock.mode:ACCEPTED}") String mode,
                             @Value("${trade.withdrawal.payout-mock.callback-delay-ms:3000}") long callbackDelayMs,
                             org.springframework.beans.factory.ObjectProvider<Clock> clockProvider,
                             @Value("${trade.withdrawal.payout-mock.app-id:}") String appId,
                             @Value("${trade.withdrawal.payout-mock.mch-id:}") String mchId,
                             @Value("${trade.withdrawal.payout-mock.api-key:}") String apiKey) {
        this(Mode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT)), Duration.ofMillis(callbackDelayMs),
                clockProvider.getIfAvailable(Clock::systemUTC),
                credentials(appId, mchId, apiKey));
    }

    /** 测试用：直接给模式、延迟、时钟与凭据（**不 sleep**，推进时钟即可）。 */
    public MockPayoutAdapter(Mode mode, Duration callbackDelay, Clock clock, Map<String, String> credentials) {
        this.mode = mode;
        this.callbackDelay = callbackDelay;
        this.clock = clock;
        this.credentials = new java.util.LinkedHashMap<>(credentials);
        // 构造期自检（而不是 @PostConstruct）：占位凭据必须让**容器启动失败**，
        // 而 @PostConstruct 在部分轻量 Runner / 代理场景下不保证被调用 —— 构造失败没有这个不确定性。
        verifyNoPlaceholderCredentials();
    }

    private static Map<String, String> credentials(String appId, String mchId, String apiKey) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("app-id", appId);
        map.put("mch-id", mchId);
        map.put("api-key", apiKey);
        return map;
    }

    /**
     * 启动自检：占位凭据必须**启动失败**，而不是运行时静默降级成「假成功」。
     *
     * <p>先例：{@code MockWechatMiniClient} 刻意不用 {@code @Primary}，就是为了让「互斥失效」
     * 变成启动失败而不是假登录成功。这里用同一条约定。
     */
    public final void verifyNoPlaceholderCredentials() {
        credentials.forEach((key, value) -> {
            if (value != null && !value.isBlank() && PLACEHOLDER.matcher(value).matches()) {
                throw new IllegalStateException("出款 mock 通道发现占位凭据 " + key + "=" + value
                        + "：请填真实值，或关掉 trade.withdrawal.payout-mock.enabled");
            }
        });
        log.warn("[payout] 出款通道为 **MOCK**（mode={}）：只用于联调与测试，channelRef 以 {} 开头",
                mode, CHANNEL_PREFIX);
    }

    @Override
    public PayoutResult pay(PayoutCommand command) {
        PayoutResult cached = terminal.get(command.payoutNo());
        if (cached != null) {
            return cached;      // 幂等：同一 payoutNo 的终态结果原样返回，绝不重复受理
        }
        PayoutResult result = decide(command);
        if (result.accepted() || "MOCK_REJECTED".equals(result.reason())) {
            terminal.putIfAbsent(command.payoutNo(), result);   // 只冻结终态
        }
        return result;
    }

    private PayoutResult decide(PayoutCommand command) {
        return switch (mode) {
            case ACCEPTED -> accept(command);
            case REJECTED -> new PayoutResult(false, true, null, "MOCK_REJECTED");   // 明确拒绝 = 终态失败
            case TIMEOUT -> new PayoutResult(false, false, null, "MOCK_TIMEOUT");     // 超时 = 未知，等 query
            case ASYNC_CALLBACK -> decideAsync(command);
        };
    }

    private PayoutResult decideAsync(PayoutCommand command) {
        Instant accepted = acceptedAt.computeIfAbsent(command.payoutNo(), key -> clock.instant());
        if (clock.instant().isBefore(accepted.plus(callbackDelay))) {
            return new PayoutResult(false, false, null, "MOCK_AWAITING_CALLBACK");   // 等回执 = 未知
        }
        PayoutResult result = accept(command);
        terminal.putIfAbsent(command.payoutNo(), result);
        return result;
    }

    private PayoutResult accept(PayoutCommand command) {
        return new PayoutResult(true, false, CHANNEL_PREFIX + command.payoutNo(), null);   // 受理 ≠ 到账
    }

    /**
     * 查询：按 mock 模式模拟真实渠道的四类查询结果。
     *
     * <p>关键映射（与真实渠道契约一致）：
     * <ul>
     *   <li>{@code TIMEOUT} ⇒ {@code UNKNOWN}（真实渠道里"超时"就是"不知道"，**不是失败**）；</li>
     *   <li>{@code ASYNC_CALLBACK} 未到回调时刻 ⇒ {@code UNKNOWN}（还在处理中）；</li>
     *   <li>{@code REJECTED} ⇒ {@code FAILED}（明确失败，可换号重发）；</li>
     *   <li>已冻结成功终态 ⇒ {@code SUCCESS}。</li>
     * </ul>
     * 这条映射本身就是用例：mock 若把 TIMEOUT 报成 FAILED，"未知态不得当失败"的规矩就形同虚设。
     */
    @Override
    public PayoutQueryResult query(String payoutNo) {
        PayoutResult frozen = terminal.get(payoutNo);
        if (frozen != null && frozen.accepted()) {
            return new PayoutQueryResult(QueryState.SUCCESS, frozen.channelRef(), null);
        }
        return switch (mode) {
            case REJECTED -> new PayoutQueryResult(QueryState.FAILED, null, "MOCK_REJECTED");
            case TIMEOUT -> new PayoutQueryResult(QueryState.UNKNOWN, null, "MOCK_TIMEOUT");
            case ASYNC_CALLBACK -> {
                Instant accepted = acceptedAt.get(payoutNo);
                boolean arrived = accepted != null && !clock.instant().isBefore(accepted.plus(callbackDelay));
                yield arrived
                        ? new PayoutQueryResult(QueryState.SUCCESS, CHANNEL_PREFIX + payoutNo, null)
                        : new PayoutQueryResult(QueryState.UNKNOWN, null, "MOCK_AWAITING_CALLBACK");
            }
            case ACCEPTED -> new PayoutQueryResult(QueryState.SUCCESS, CHANNEL_PREFIX + payoutNo, null);
        };
    }

    /** 供测试与运维观测：已冻结的终态数（真实渠道里对应「已出款笔数」）。 */
    public int settledCount() {
        return terminal.size();
    }

    /** 凭据键（Spring 注入时用 map 绑定；测试直接传）。 */
    public static List<String> credentialKeys() {
        return CREDENTIAL_KEYS;
    }
}
