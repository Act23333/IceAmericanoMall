package org.icedamericanomall.pay.app;

/**
 * 发票联动补偿重试的**批量与租约策略**（常量 + 取值依据）。
 *
 * <p>从 {@link InvoiceLinkCompensationRetryManager} 抽出的原因有二：① 那段「租约为什么是这个数」
 * 的推导很长，留在编排类里会把类撑过 300 行（硬约束，且有棘轮门禁）；② 这几个值必须**成组**
 * 阅读才能判断对错（批大小 × 单次超时上限 × 安全余量），分散在方法之间最容易被后人只改一个。
 *
 * <p><b>租约是什么</b>：认领成功后的一段时间盒 —— 该行在这段时间内不会再被别的执行者认领。
 * 到期即自动回到可认领集合，**不需要回收任务**（用「会自愈的时间盒」代替「会卡住的状态」）。
 *
 * <p><b>为什么 {@code CLAIM_LEASE_SECONDS} 要按最坏情况算（真实缺陷）</b>：首版取 120s，
 * 依据是「100 条 × 单笔 ~200ms ≈ 20s」。那只看了**平均耗时**，漏了**降级态**：Feign 的
 * TimeLimiter 上限是 5s，下游全部超时时单批最坏 = {@link #BATCH_LIMIT} × 5s = 500s。
 * 于是 120s 的租约在批处理还没跑完时就过期，另一个执行者会把**仍在处理**的行认领走 ——
 * 认领机制在最需要它的场景下失效。
 *
 * <p><b>接缝常量</b>：{@link #FEIGN_CALL_TIMEOUT_SECONDS} 必须等于 {@code application.yml} 里
 * {@code resilience4j.timelimiter} 的 {@code timeout-duration}。两处漂移会让上面的推导失效，
 * 故由 {@code InvoiceLinkCompensationLeaseBoundaryTest} 直接读 yml 交叉断言（改超时不改租约即红）。
 *
 * <p><b>时钟口径（适用边界）</b>：写入与比较**都用应用时钟**（{@code LocalDateTime.now()} 在 Java
 * 侧算好作为参数传入 SQL；不用 {@code DATE_ADD(..., INTERVAL ...)}，因为 MySQL/H2 日期方言不一致）。
 * 同节点内绝对自洽；**跨节点依赖 NTP** —— 时钟严重漂移（分钟级）时租约可能被判提前过期，
 * 出现一次并发重调，此时靠下游幂等（trade 侧「无有效票 = 空操作」）兜底，不会重复冲红。
 * <b>租约 ≠ 分布式锁</b>：它缩小重复窗口，不承诺「恰好一次」；要承诺需 ADR-024 的
 * {@code DistributedLock} 或 XXL 分片调度。
 */
final class InvoiceLinkCompensationLeasePolicy {

    /**
     * 单轮最大处理条数：限制单轮 Feign 调用总耗时；积压靠多轮滚动消化（到期时间升序，老单优先收敛）。
     * 与 {@code RefundResumeManager} 的批大小同口径。
     */
    static final int BATCH_LIMIT = 100;

    /**
     * 单次 Feign 调用的**上限**（秒）—— 必须等于 {@code application.yml} 的
     * {@code resilience4j.timelimiter.configs.default.timeout-duration}（接缝，见类注释）。
     */
    static final int FEIGN_CALL_TIMEOUT_SECONDS = 5;

    /**
     * 单批**最坏耗时**（秒）= {@link #BATCH_LIMIT} × {@link #FEIGN_CALL_TIMEOUT_SECONDS}。
     *
     * <p>不是「平均 200ms × 条数」：降级态下每一笔都会走到超时上限，这个值才是租约要覆盖的量级。
     */
    static final int WORST_CASE_BATCH_SECONDS = BATCH_LIMIT * FEIGN_CALL_TIMEOUT_SECONDS;

    /**
     * 认领租约时长（秒）= {@link #WORST_CASE_BATCH_SECONDS} × 1.2（安全余量）。
     *
     * <p>三条约束：① 必须 &gt; 单批最坏耗时（否则「自己还在重调、别人已接手」）；
     * ② 远大于 {@code @Scheduled} 间隔（默认 60s），上一轮没跑完时下一轮不会抢走；
     * ③ 必须短到崩溃后可尽快恢复 —— 持有者崩溃后最多等一个租约周期（10 分钟）重新可认领。
     */
    static final int CLAIM_LEASE_SECONDS = WORST_CASE_BATCH_SECONDS * 6 / 5;

    private InvoiceLinkCompensationLeasePolicy() {
    }
}
