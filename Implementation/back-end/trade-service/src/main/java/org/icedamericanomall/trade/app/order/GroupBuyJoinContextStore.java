package org.icedamericanomall.trade.app.order;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;

/**
 * 拼团参团上下文存取（F13, ADR-049）—— 下单期写入、支付成功时一次性消费的 trade 侧防腐缓存。
 *
 * <p><b>为何存在</b>：I53 join 的 {@code activityId} 必填（开团需要活动归属），而支付回调到达时
 * 下单期的 {@code OrderCreateContext} 已消亡、orders 表亦无活动列（schema 不动）——故 T27 下单成功
 * 后把 {@code orderNo → (activityId, teamNo)} 记入 Redis，支付成功参团时读取并删除（一次性语义）。
 *
 * <p><b>键空间纪律</b>：trade 侧自管键前缀 {@code groupbuy:order:ctx:}，不与其他服务共享 Redis 键
 * （硬约束）。TTL 48 小时覆盖「成团时限 + 30 分钟支付超时」的最坏窗口，未支付订单取消后残留键
 * 由 TTL 兜底回收。
 *
 * <p><b>缺失语义</b>：键不存在（过期/丢失）或读取异常均返回 {@code null}，由
 * {@link GroupBuyParticipationService} 按不可参团处理并走退款闭环——绝不静默假装参团成功
 * （拼团单停留在团外 = 用户以拼团价买到非拼团商品）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyJoinContextStore {

    /** 上下文键前缀（trade 侧自管键空间） */
    static final String CTX_KEY_PREFIX = "groupbuy:order:ctx:";

    /** TTL 48 小时 —— 覆盖成团时限 + 30 分钟支付超时的最坏窗口 */
    static final Duration CTX_TTL = Duration.ofHours(48);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 下单成功后写入参团上下文（T27 策略 afterCreate 阶段调用）。
     *
     * <p>写失败异常直接上抛：上下文写不进去 = 该单支付后必然无法参团，宁可下单即失败
     * （Saga 补偿已扣库存），也不让用户付款后陷入自动退款闭环。
     *
     * @param orderNo    拼团订单号
     * @param activityId 拼团活动 id（必填）
     * @param teamNo     团号（空 = 开团）
     */
    public void save(String orderNo, Long activityId, String teamNo) {
        GroupBuyJoinCtx ctx = new GroupBuyJoinCtx(activityId, teamNo);
        stringRedisTemplate.opsForValue().set(CTX_KEY_PREFIX + orderNo, JSONUtil.toJsonStr(ctx), CTX_TTL);
    }

    /**
     * 读取并删除参团上下文（一次性消费语义）。
     *
     * @param orderNo 拼团订单号
     * @return 上下文；键不存在或读取异常返回 {@code null}（调用方按不可参团处理）
     */
    public GroupBuyJoinCtx take(String orderNo) {
        try {
            String key = CTX_KEY_PREFIX + orderNo;
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            stringRedisTemplate.delete(key);
            return JSONUtil.toBean(json, GroupBuyJoinCtx.class);
        } catch (Exception e) {
            log.error("拼团参团上下文读取失败，按缺失处理: orderNo={}", orderNo, e);
            return null;
        }
    }

    /** 参团上下文（JSON 序列化存 Redis）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupBuyJoinCtx implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 拼团活动 id（I53 join 必填） */
        private Long activityId;

        /** 团号（空 = 开团） */
        private String teamNo;
    }
}
