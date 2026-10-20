package org.icedamericanomall.trade.app.finance;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository.StalePaying;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 「付款中」停留告警。
 *
 * <p><b>为什么要它</b>：`PAYING` 不会自己消失。单子卡在那里，冻结额被永久占住而无人知晓 ——
 * 商家额度被无声吃掉，比"报错"更难发现。
 *
 * <p><b>告警节奏靠「派生代次」</b>：`gen = 停留分钟 / N`。**代次变化才告警**，同一代次内按
 * `payoutNo` 去重 ⇒ 不用任何状态表就同时得到「首次即告」「每 N 分钟再告一次」「不刷屏」。
 * `N` 与出款任务扫描的 timeout **同源同一个配置项**（{@code stale-alert-minutes}）。
 *
 * <p><b>告警体结构化并带"下一步"</b>：`payoutNo` / 停留时长 / 代次 / 上次出款结果 / 渠道查询入口。
 * **不含**银行账号、开户行、商户凭据 —— 告警会进工单与告警平台，这些字段不该跟着走。
 *
 * <p><b>已知局限</b>：去重集合是进程内的（重启或多实例会重复告警）。
 * 接告警平台后由平台按 `payoutNo+gen` 去重；在那之前这是可接受的（宁可重复，不可漏报）。
 */
@Slf4j
@Service
public class PayoutStaleAlerter {

    /** 单次扫描上限。 */
    private static final int DEFAULT_LIMIT = 100;

    /** 下一步做什么：给的是**入口**，不是"请排查"。 */
    static final String NEXT_ACTION =
            "查渠道流水（channel=MOCK 时按 payoutNo 查应用日志；真实渠道查渠道后台流水）→ "
                    + "管理端 /api/admin/finance/withdrawals/{id} 人工确认；禁止直接释放冻结";

    private final WithdrawalRepository withdrawalRepository;
    private final Clock clock;
    private final WithdrawalPayoutService payoutService;

    /** 停留阈值（分钟）：与出款任务扫描的下界同源。 */
    @Value("${trade.withdrawal.payout.stale-alert-minutes:30}")
    private int staleMinutes = 30;

    /** 本次实际发出的告警数（测试观测用；去重已交给 DB 的唯一键）。 */
    private int raisedCount;

    public PayoutStaleAlerter(WithdrawalRepository withdrawalRepository,
                              org.springframework.beans.factory.ObjectProvider<Clock> clockProvider,
                              WithdrawalPayoutService payoutService) {
        this(withdrawalRepository, clockProvider.getIfAvailable(Clock::systemUTC), payoutService);
    }

    /** 测试用：显式时钟（与 mock 通道共用同一 Clock 是 B1 的硬要求）。 */
    public PayoutStaleAlerter(WithdrawalRepository withdrawalRepository, Clock clock,
                              WithdrawalPayoutService payoutService) {
        this.withdrawalRepository = withdrawalRepository;
        this.clock = clock;
        this.payoutService = payoutService;
    }

    /**
     * 扫一遍并告警。
     *
     * @param limit 扫描上限
     * @return 本次真正发出（未被去重）的告警数
     */
    public int alertStale(int limit) {
        int threshold = Math.max(1, staleMinutes);
        LocalDateTime cutBefore = LocalDateTime.ofInstant(
                clock.instant().minus(Duration.ofMinutes(threshold)), ZoneId.systemDefault());
        List<StalePaying> stale = withdrawalRepository.findStalePaying(cutBefore, limit);

        int raised = 0;
        for (StalePaying row : stale) {
            long dwellMinutes = Math.max(0, Duration.between(row.createTime(), now()).toMinutes());
            int gen = (int) (dwellMinutes / threshold);
            if (gen < 1) {
                continue;       // 还没到第一个窗口
            }
            // 去重交给 DB 唯一键（payout_no, alert_gen）——进程内集合在多实例下会重复告警
            if (withdrawalRepository.markAlertedOnce(row.payoutNo(), gen) == 0) {
                continue;       // 同一代次已告过
            }
            log.error("{}", body(row, dwellMinutes, gen));
            raisedCount++;
            raised++;
        }
        return raised;
    }

    /**
     * 结构化告警体（一行 JSON；后续可直接接告警平台）。
     *
     * @param row          单据
     * @param dwellMinutes 已停留分钟
     * @param gen          代次
     * @return JSON 字符串
     */
    String body(StalePaying row, long dwellMinutes, int gen) {
        return "{\"event\":\"WITHDRAWAL_PAYING_STALE\""
                + ",\"payoutNo\":\"" + row.payoutNo() + "\""
                + ",\"withdrawalId\":" + row.id()
                + ",\"sellerId\":" + row.sellerId()
                + ",\"amount\":" + row.amount()
                + ",\"dwellMinutes\":" + dwellMinutes
                + ",\"gen\":" + gen
                + ",\"lastPay\":\"" + payoutService.lastResult(String.valueOf(row.payoutNo())) + "\""
                + ",\"nextAction\":\"" + NEXT_ACTION + "\"}";
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }

    /** 测试/运维观测：本进程累计发出的告警数。 */
    int raisedCount() {
        return raisedCount;
    }

    static int defaultLimit() {
        return DEFAULT_LIMIT;
    }
}
