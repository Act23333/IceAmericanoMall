package org.icedamericanomall.trade.app.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 券核销兜底对账标记（V5.19 资损修复）—— 下单期「即将核销券」的崩溃可恢复凭证。
 *
 * <p><b>为何存在</b>：Saga 补偿（{@code OrderCreationManager#compensateOnFailure}）只覆盖
 * 「进程存活且异常可捕获」的失败。若进程在下单中途被杀死（容器驱逐 / OOM / JVM 崩溃），
 * marketing 侧券核销事务<b>已提交</b>而 trade 侧既无订单、也无补偿执行者 —— 用户券被静默吞掉。
 * 故在下单进入「可能核销券」的阶段<b>之前</b>落一条「处理中」标记，成交或回收成功后清除；
 * 残留标记由 {@link CouponReconcileManager} 定时对账回收。
 *
 * <p><b>键空间纪律</b>：trade 侧自管键前缀 {@code coupon:consumed:pending}，不与其他服务共享
 * Redis 键（硬约束）。用 ZSET（member=订单号，score=尝试时间戳）而非普通字符串键：
 * 既可按时间窗扫描（宽限窗口过滤），又天然按订单号去重。
 *
 * <p><b>幂等与语义</b>：标记只表示「本次下单<b>可能</b>已核销券」——写入方在核销<b>之前</b>落标记，
 * 必然包含「核销未发生」（如核销前的 I44 促销失败）与「核销已发生」两种情形。因此对账侧<b>绝不</b>
 * 仅凭标记回滚：必须先确认 trade 侧确实<b>不存在</b>该订单，才调用 marketing 的
 * {@code rollbackByOrderNo}（其 {@code findUsedByOrderNo} + restore 天然幂等，无法命中即空操作）。
 * 方向性保证：误报只产生一次无害的空回滚，绝不出现「订单已成交却被回滚券」的反向资损。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponConsumptionLedger {

    /** 待对账标记键（trade 侧自管键空间，ZSET：member=orderNo，score=尝试时间戳毫秒） */
    static final String PENDING_KEY = "coupon:consumed:pending";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 标记「订单号 X 可能已核销券，尚未确认最终状态」（下单进入核销阶段前调用，幂等）。
     *
     * @param orderNo 订单号（空/空白 = 无幂等锚点，直接忽略）
     */
    public void markPending(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(PENDING_KEY, orderNo, System.currentTimeMillis());
    }

    /**
     * 清除标记（订单已成交、或券已确认回收、或对账已处理完毕；幂等）。
     *
     * @param orderNo 订单号（空/空白 = 无操作）
     */
    public void clear(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(PENDING_KEY, orderNo);
    }

    /**
     * 取「尝试时间早于 cutoffMillis」的待对账订单号（按时间升序，最多 limit 条）。
     *
     * <p>宽限窗口由调用方传入：刚写入的标记对应的下单请求可能仍在进行中，
     * 对账必须跳过它们，避免与在途流程竞态。
     *
     * @param cutoffMillis 时间上界（epoch 毫秒，含）
     * @param limit        单轮上限
     * @return 待对账订单号；无标记返回空列表
     */
    public List<String> listPendingBefore(long cutoffMillis, int limit) {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .rangeByScore(PENDING_KEY, 0, cutoffMillis, 0, limit);
        return members == null ? List.of() : new ArrayList<>(members);
    }
}
