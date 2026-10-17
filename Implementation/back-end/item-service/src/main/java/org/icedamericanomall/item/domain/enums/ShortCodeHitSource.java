package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 短码解析命中来源（{@link org.icedamericanomall.item.domain.port.ShortCodeResolver} 的指标维度）。
 *
 * <p><b>为什么要把它定义在领域层</b>：命中率是「这个承载位到底靠哪一层撑住」的唯一可观测证据 ——
 * 如果 {@link #DB} 计数长期占满，说明缓存没生效（或键写错了）；如果 {@link #NEGATIVE} 暴涨，
 * 说明有人在批量猜码或物料已作废。把维度作为<b>端口契约的一部分</b>，实现方就无法
 * 「只做缓存、不报来源」，测试也才能断言「清空 L1+L2 后仍能解析」。
 *
 * <p><b>四类互斥</b>（一次解析只计入一个来源）：
 * <ul>
 *   <li>{@link #LOCAL} —— Caffeine 本地缓存命中（映射不可变，<b>不做失效监听</b>）；</li>
 *   <li>{@link #REDIS} —— Redis 命中（跨实例共享层）；</li>
 *   <li>{@link #DB} —— 落到真相源（{@code uk_short_code} 等值查）并回填两级缓存；</li>
 *   <li>{@link #NEGATIVE} —— 负缓存命中或刚判定的「不存在」（防穿透，30–60s）。</li>
 * </ul>
 */
@Getter
public enum ShortCodeHitSource {

    /** 本地缓存（Caffeine）命中。 */
    LOCAL("local"),

    /** Redis 命中。 */
    REDIS("redis"),

    /** 真相源（DB）命中。 */
    DB("db"),

    /** 负缓存命中 / 判定为不存在。 */
    NEGATIVE("negative");

    /** 指标维度名（小写，日志与监控标签口径）。 */
    private final String tag;

    ShortCodeHitSource(String tag) {
        this.tag = tag;
    }
}
