package org.icedamericanomall.trade.app.finance;

import org.icedamericanomall.common.utils.IdGenerator;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.TradeConverter;
import org.icedamericanomall.trade.adapter.response.SellerBalanceVO;
import org.icedamericanomall.trade.adapter.response.SettlementVO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.enums.SettlementStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.SellerFundAccount;
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算用例族编排（应用层）—— 生成结算单 / 结算 / 查询 / 可提现余额 / 打款记账。
 *
 * <p><b>T-WITHDRAW 第一步的边界（写死，防止下一步做重或做漏）</b>：
 * <ul>
 *   <li>本批只动 {@code seller_fund_account} 的两个累计字段 —— {@code settled_total}（结算加）、
 *       {@code paid_total}（打款加）；</li>
 *   <li><b>不碰 {@code frozen_amount}</b>：它的「加」在第二步 {@code applyWithdrawal} 预占时才出现，
 *       先做减法等于扣一个从未加过的字段（余额凭空变小，且与结算单对不上）；</li>
 *   <li><b>结算单行不打「已打款」标</b>：{@code settlement.status = SETTLED} 的语义是「<b>可提现</b>」，
 *       <b>不是「未打款」</b>；「已打款」的事实源是 {@code withdrawal.status = PAID_OUT}
 *       （聚合看 {@code seller_fund_account.paid_total}）。所以打款后不回写结算单是<b>设计</b>，不是权宜；
 *       旧 {@code markSellerSettlementsPaidOut} 的「按商家全量置位」已被删除（提现 100 会把 1000 全标成已打款、
 *       剩 900 看似再也提不出来，T-WITHDRAW-2），且<b>不打算恢复</b>。
 *       「结算单列表要区分已打款」属**读模型**（join {@code withdrawal}），不是改行状态。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SettlementManager {

    /**
     * 平台抽成比例 —— 5%（以百分比整数表示，避免浮点误差：佣金 = 收入 * 5 / 100）。
     */
    private static final int COMMISSION_RATE_PERCENT = 5;

    /** 百分比基数。 */
    private static final int PERCENT_BASE = 100;

    private final SettlementRepository settlementRepository;
    private final SellerFundAccountRepository sellerFundAccountRepository;
    private final OrderRepository orderRepository;
    private final TradeConverter tradeConverter;

    /** 生成商家结算单（管理员触发；收入口径 = 买家实付 payAmount）。 */
    @Transactional(rollbackFor = Exception.class)
    public SettlementVO generate(Long sellerId, String start, String end) {
        LocalDate periodStart = LocalDate.parse(start), periodEnd = LocalDate.parse(end);
        List<org.icedamericanomall.trade.domain.model.order.Order> orders =
                orderRepository.findBySellerAndStatusAndEndTimeBetween(sellerId,
                        OrderStatusEnum.COMPLETED.getCode(),
                        periodStart.atStartOfDay(), periodEnd.plusDays(1).atStartOfDay());

        // 收入口径 = 买家实付 payAmount（非原价 totalAmount），保证扣除优惠券后的平台抽成正确
        int total = orders.stream().mapToInt(o -> o.getPayAmount() != null ? o.getPayAmount() : 0).sum();
        int commission = total * COMMISSION_RATE_PERCENT / PERCENT_BASE;
        int settlementAmount = total - commission;

        Settlement settlement = new Settlement();
        settlement.setSettlementNo(IdGenerator.nextId());
        settlement.setSellerId(sellerId);
        settlement.setPeriodStart(periodStart);
        settlement.setPeriodEnd(periodEnd);
        settlement.setOrderCount(orders.size());
        settlement.setTotalAmount(total);
        settlement.setCommission(commission);
        settlement.setSettlementAmount(settlementAmount);
        settlement.setStatus(SettlementStatusEnum.PENDING.getCode());
        return tradeConverter.toVO(settlementRepository.save(settlement));
    }

    /**
     * 结算单置为「已结算」 —— <b>PENDING→SETTLED 的唯一入口</b>
     * （定时任务与手动兜底都调本方法；触发方式后定，权威方案不因触发方式退化）。
     *
     * <p><b>可结算条件全部字段化</b>（status / period_end / settlement_amount / order_count），
     * 由 {@link SettlementRepository#settleConditionally} 一条条件更新 + 影响行数判定实现 ——
     * 不用行锁，并发下只有一个事务拿到 1 行。
     *
     * <p><b>同事务累加余额</b>：命中 1 行时立刻把 {@code settlement_amount} 加到
     * {@code seller_fund_account.settled_total}。<b>这里必须是同一个事务</b>：
     * 状态变了而余额没加 = 钱凭空消失；余额加了而状态没变 = 下一轮重复累加。
     *
     * <p><b>为什么先读一次结算单</b>：需要 {@code sellerId} 与 {@code settlement_amount}；
     * 这两个字段在结算之后不再变化，而「到底结没结」由条件更新的影响行数判定，<b>不由这次读决定</b>
     * —— 读到的状态陈旧不会造成重复累加。
     *
     * @return true=本次真的完成结算；false=条件不满足（已结算 / 未到期 / 零额 / 无订单 / 不存在），幂等 no-op
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean settle(Long settlementId) {
        LocalDateTime now = LocalDateTime.now();
        return settleAt(settlementId, now.toLocalDate(), now);
    }

    /**
     * 在<b>给定时刻</b>结算 —— 批处理必须整批共用同一对 {@code today} / {@code now}。
     *
     * <p>为什么不能每条自己取时钟：一次扫描跨零点时，前半批按 D 判、后半批按 D+1 判，
     * 而候选查询用的是扫描开始时的 D ⇒ 同一次扫描里的判定口径会分叉（重复扫描还会得到不同结果）。
     * 时钟由 {@code SettlementSweeper} 取一次往下传，本方法不再调 {@code now()}。
     *
     * <p><b>为什么单独起名而不重载 {@code settle}</b>：真正的写就在这里，门禁的「已知资金方法」
     * 按「文件#方法名」定位 body —— 若叫 {@code settle} 重载，门禁只会检查到那个一行的委托版，
     * 等于把「body 完整」这条断言架空。命名分叉是为了让门禁看得见真正的写。
     *
     * <p>其余语义与 {@link #settle(Long)} 完全一致（条件更新 + 同事务累加），单条仍是幂等 no-op。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean settleAt(Long settlementId, LocalDate today, LocalDateTime now) {
        Settlement settlement = settlementRepository.findById(settlementId);
        if (settlement == null) {
            return false;
        }
        int rows = settlementRepository.settleConditionally(settlementId,
                SettlementStatusEnum.PENDING.getCode(), SettlementStatusEnum.SETTLED.getCode(),
                today, now);
        if (rows == 0) {
            return false;
        }
        sellerFundAccountRepository.addSettled(settlement.getSellerId(),
                settlement.getSettlementAmount() == null ? 0 : settlement.getSettlementAmount());
        return true;
    }

    /**
     * 打款记账：{@code paid_total += amount}。
     *
     * <p><b>这里刻意不做额度判断。</b>额度（「可提现额 ≥ amount」）属于<b>申请侧</b> ——
     * {@code applyWithdrawal} 在第二步改成条件更新预占 {@code frozen_amount}，额度判定与冻结是同一件事。
     * 审核侧只回答两个问题：①「这笔单还能不能动」（条件迁移，0 行即幂等）；②「记账」。
     *
     * <p>把额度判断放在这里会制造一种<b>假拒绝</b>：申请时通过了、打款时因为期间余额变化（例如另一笔先打了款）
     * 被拒，而钱其实已经按申请时的额度付出去了 —— 状态与资金两边都不对。
     *
     * <p>金额 {@code <= 0} 仍拒绝：那是数据不合法（提现单的 {@code amount} 在申请侧已校验 > 0），
     * 与「额度够不够」是两件事。
     *
     * <p><b>只加 {@code paid_total}、不减 {@code settled_total}</b>：后者是「累计已结算」，
     * 与「Σ 结算单金额」一一对应、可直接对账；若两边同时动，同一笔钱会被扣两次，
     * 且按金额部分置位结算单时两边必然对不上。
     */
    @Transactional(rollbackFor = Exception.class)
    public void payout(Long sellerId, int amount) {
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "打款金额必须大于 0");
        }
        sellerFundAccountRepository.addPaid(sellerId, amount);
    }

    /** 商家结算单分页（创建时间倒序）。 */
    public IPage<SettlementVO> pageBySeller(Long sellerId, int page, int size) {
        return settlementRepository.pageBySeller(sellerId, page, size).convert(tradeConverter::toVO);
    }

    /** 商家结算单详情（含归属校验）。 */
    public SettlementVO getSellerSettlement(Long id, Long sellerId) {
        Settlement settlement = settlementRepository.findById(id);
        if (settlement == null || !settlement.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return tradeConverter.toVO(settlement);
    }

    /**
     * 商家可提现余额（单位：分）= 资金账户的可提现额 = 已结算合计 − 待审冻结 − 已打款。
     *
     * <p>口径从「Σ 状态为已结算的结算单」迁到 {@code seller_fund_account}（T-WITHDRAW）：
     * 旧口径既不扣已打款也不扣待审提现，同一商家可连续申请多笔、每笔都能过校验。
     * <b>更根本的原因</b>：结算单压根不承载打款事实（{@code SETTLED} 只表示「可提现」，
     * 已打款的事实源是 {@code withdrawal}）⇒ 无论怎么过滤 {@code settlement.status} 都算不出「还能提多少」。
     * 没有账户行（从未结算过）返回 0。
     */
    public int availableBalance(Long sellerId) {
        SellerFundAccount account = sellerFundAccountRepository.findBySellerId(sellerId);
        return account == null ? 0 : account.available();
    }

    /**
     * 商家可提现余额视图（T22 {@code GET /api/seller/finance/balance}）。
     *
     * <p><b>批次 D</b>：该端点此前在 Controller 内以 {@code Map.of("sellerId", …, "balance", …)}
     * 就地拼弱类型报文（违反「Controller 只做参数校验 + 调用 Manager + 包 Result」），
     * 现由本方法装配 VO。数值口径委托 {@link #availableBalance(Long)}（见其 javadoc 的口径说明）。
     *
     * @param sellerId 商家标识（{@code seller.user_id}；由适配层从 {@code UserContext} 取，禁止信任前端传参）
     * @return 余额视图对象（{@code balance} 为分，口径见 {@link org.icedamericanomall.trade.adapter.response.SellerBalanceVO}）
     */
    public SellerBalanceVO sellerBalance(Long sellerId) {
        SellerBalanceVO vo = new SellerBalanceVO();
        vo.setSellerId(sellerId);
        vo.setBalance(availableBalance(sellerId));
        return vo;
    }

    /** 管理后台：全部结算单分页（创建时间倒序）。 */
    public IPage<SettlementVO> pageAll(int page, int size) {
        return settlementRepository.pageAll(page, size).convert(tradeConverter::toVO);
    }
}
