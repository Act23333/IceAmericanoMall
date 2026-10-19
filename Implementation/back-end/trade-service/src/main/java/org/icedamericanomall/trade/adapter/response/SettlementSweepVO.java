package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.util.List;

/**
 * 管理端「批量结算」响应。
 *
 * <p><b>四个计数分开给</b>（不要合并成一个"成功数"）：
 * {@code skipped} = 条件不满足（已结算 / 账期未到 / 零额 / 无订单，**正常现象**）；
 * {@code failed} = 抛异常（**要人看**）；{@code healthy} 直接给运维判断依据。
 *
 * <p><b>{@code scanned} 的语义要说清</b>：它是**本次**扫到的候选数，不是「待结算队列长度」。
 * 调度（XXL-Job）与手动触发可能同时跑，两边都会扫到同一批单 ——
 * 所以 <b>{@code scanned} 不可跨次相加</b>，也不代表「重启前还剩多少没结」。
 * 真正剩多少要另查库（{@code status = PENDING AND period_end <= today ...}）。
 *
 * <p>{@code failures} 只带前若干条（服务端限制），是给运维**当场定位**用的样本，不是全量错误清单；
 * 全量在应用日志里。
 */
@Data
public class SettlementSweepVO {

    private int scanned;

    private int settled;

    private int skipped;

    private int failed;

    private boolean healthy;

    private List<Failure> failures;

    /** 一条失败明细：哪张结算单、什么原因。 */
    @Data
    public static class Failure {

        private Long settlementId;

        private String reason;
    }
}
