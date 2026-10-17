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
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 结算用例族编排（应用层）—— 生成结算单 / 查询 / 可提现余额 / 打款级联。
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

    /** 商家可提现余额 = 所有已结算未打款金额之和（单位：分）。 */
    public int availableBalance(Long sellerId) {
        return settlementRepository.findBySellerAndStatus(sellerId, SettlementStatusEnum.SETTLED.getCode())
                .stream()
                .mapToInt(s -> s.getSettlementAmount() != null ? s.getSettlementAmount() : 0)
                .sum();
    }

    /**
     * 商家可提现余额视图（T22 {@code GET /api/seller/finance/balance}）。
     *
     * <p><b>批次 D</b>：该端点此前在 Controller 内以 {@code Map.of("sellerId", …, "balance", …)}
     * 就地拼弱类型报文（违反「Controller 只做参数校验 + 调用 Manager + 包 Result」），
     * 现由本方法装配 VO。<b>数值口径不变</b> —— 仍委托 {@link #availableBalance(Long)}。
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

    /** 提现打款成功后，将该商家已结算单据置为已打款。 */
    @Transactional(rollbackFor = Exception.class)
    public void markSellerSettlementsPaidOut(Long sellerId) {
        settlementRepository.updateStatusBySellerAndStatus(sellerId,
                SettlementStatusEnum.SETTLED.getCode(), SettlementStatusEnum.PAID_OUT.getCode());
    }
}
