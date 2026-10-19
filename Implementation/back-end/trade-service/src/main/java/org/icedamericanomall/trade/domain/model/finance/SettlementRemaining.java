package org.icedamericanomall.trade.domain.model.finance;

/**
 * 一张结算单的「还可核销额」（只读投影，供核销规划用）。
 *
 * <p>为什么要单独一个类型：核销规划是<b>纯函数</b>（{@code domain/service/SettlementAllocator}），
 * 不能把 {@code Settlement} 整个聚合塞给它 —— 那样会把「规划」和「持久化状态」绑在一起，
 * 单测就得造一堆无关字段。这里只要两个数：哪张单、还剩多少。
 *
 * @param settlementId 结算单技术主键
 * @param remaining    可核销余额（分）= {@code settlement_amount − withdrawn_amount}，恒 ≥ 0
 */
public record SettlementRemaining(Long settlementId, int remaining) {
}
