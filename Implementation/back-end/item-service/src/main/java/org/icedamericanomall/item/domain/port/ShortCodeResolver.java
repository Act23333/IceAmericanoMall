package org.icedamericanomall.item.domain.port;

import org.icedamericanomall.item.domain.enums.ShortCodeHitSource;

/**
 * 短码解析端口（领域层，<b>框架无关</b>）—— 「短码 → SPU 业务键」的唯一解析契约。
 *
 * <p><b>为什么需要端口而不是直接查库</b>：短码是<b>印在物理介质上的承载位码</b>
 * （小程序码落地页是 C 端高频只读入口），解析必须能走多级缓存；而领域层不得依赖
 * Caffeine / Redis（ADR-038 分层守卫）。于是「解析能力」抽成端口，三级缓存的
 * 技术细节全部收在基础设施实现（{@code infrastructure.adapter}）里，
 * {@code SpuManager} 只看见「给我业务键 / 没有就 null」。
 *
 * <p><b>实现必须满足的三道防线（ADR-063 §决策 8 + 09 约束）</b>：
 * <ol>
 *   <li><b>防穿透</b>：不存在的短码要进负缓存（30–60s），避免每次猜码都打库；</li>
 *   <li><b>防击穿</b>：同一短码的并发重建必须互斥（single-flight），不能整批打库；</li>
 *   <li><b>无 Redis 也要能解析</b>：Redis 不可用 → <b>回落下一层</b>（fail-open to DB），
 *       <b>绝不</b>把「Redis 挂了」当成「短码不存在」—— 前者是基础设施故障，后者是业务结论。</li>
 * </ol>
 *
 * <p><b>DB 是唯一真相源</b>：清空 L1 + L2 之后仍必须能解析成功（这是本端口的验收硬断言）。
 *
 * <p><b>不负责的事</b>：不判断商品状态（下架/删除由应用层按 P2 同一口径处理），
 * 不返回聚合（返回的是 <b>SPU 业务键</b>，让应用层复用既有详情装配，避免第二套装配逻辑）。
 */
public interface ShortCodeResolver {

    /**
     * 短码 → SPU <b>业务键</b>（32-hex）。
     *
     * @param shortCode 短码（形态已由适配层 {@code ShortCode#require} 校验；本方法仍对非法形态返回
     *                  {@code null}，作内部防御）
     * @return SPU 业务键；短码不存在 → {@code null}（调用方按「资源不存在」处理）
     * @throws org.icedamericanomall.common.exception.BizException
     *         真相源不可用（{@code INTERNAL_ERROR}）—— DB 故障不得伪装成「查不到」
     */
    String resolve(String shortCode);

    /**
     * 某个命中来源的累计计数（监控 / 单测断言用；进程内计数，不做持久化）。
     *
     * @param source 命中来源维度
     * @return 累计次数（从进程启动起算）
     */
    long hitCount(ShortCodeHitSource source);
}
