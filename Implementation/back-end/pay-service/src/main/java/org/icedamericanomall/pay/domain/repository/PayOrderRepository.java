package org.icedamericanomall.pay.domain.repository;

import org.icedamericanomall.pay.domain.model.payorder.PayOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单仓储（领域端口）—— 实现方：{@code infrastructure.persistence.PayOrderRepositoryImpl}。
 *
 * <p><b>并发语义</b>：所有状态迁移均为条件更新（{@code WHERE status = 期望值}），
 * 返回 boolean 表示是否命中；「是否命中」由数据库回答，业务判断由 {@link PayOrder} 聚合承担。
 *
 * <p><b>寻址口径（资金链路硬约束）</b>：外部渠道（回调 / 账单）只认「对外渠道号」
 * {@code channel_trade_no}，因此回调与对账必须走 {@link #getByChannelTradeNo(String)}；
 * 内部号 {@link #getByPayOrderNo(String)} 只用于本服务内部与管理端查询。
 * 二者<b>不可互换</b> —— 渠道永远不会把 {@code pay_order_no} 传回来。
 */
public interface PayOrderRepository {

    /** 按业务订单号取最新一条（防多行炸裂：历史可能有多条支付记录）。 */
    PayOrder getByBizOrderNo(String bizOrderNo);

    /** 按支付单号取（业务唯一键；<b>仅内部口径</b>，渠道侧不用此键）。 */
    PayOrder getByPayOrderNo(String payOrderNo);

    /**
     * 按对外渠道交易号取（业务唯一键之一）—— <b>渠道回调 / 对账账单的唯一寻址入口</b>。
     *
     * @param channelTradeNo 对外渠道交易号（{@code pay_order.channel_trade_no}）
     * @return 支付单；不存在返回 {@code null}
     */
    PayOrder getByChannelTradeNo(String channelTradeNo);

    /**
     * 新增「待支付」支付单（创建口径由 {@link PayOrder#newPending} 内聚，含对外渠道交易号），
     * 返回回填 id 后的聚合。支付链接由渠道下单后经 {@link #attachPayUrl} 回填 ——
     * 先把渠道号落库再调渠道，才能保证任何时刻收到的回调都查得到本地支付单。
     */
    PayOrder createPending(String bizOrderNo, Long userId, int amount, String channelCode, int payType);

    /**
     * 复用「待支付」单：条件更新金额/渠道/支付方式/超时时间（WHERE status=待支付），
     * 并清空旧支付链接（旧渠道的二维码对新渠道/新金额无效）。
     *
     * <p><b>对外渠道交易号不重置</b> —— 跨重试稳定是幂等前提（见 {@link PayOrder#applyPendingReset}）。
     *
     * @return 命中并应用重置后的聚合；未命中（状态已变更）返回 {@code null}
     */
    PayOrder resetPending(PayOrder existing, int amount, String channelCode, int payType);

    /**
     * 回填支付链接（WHERE id + status=待支付）。
     *
     * @param payOrder 目标支付单聚合；命中时同步回写其内存态 {@code qrCodeUrl}
     * @param payUrl   渠道返回的支付链接 / 二维码
     * @return 是否命中（未命中说明状态已推进，链接已无意义）
     */
    boolean attachPayUrl(PayOrder payOrder, String payUrl);

    /** 回调置成功（WHERE payOrderNo + status=待支付）。 */
    boolean markSuccessIfPending(String payOrderNo, String resultCode);

    /** 余额支付置成功（WHERE id + status=待支付）。 */
    boolean markBalancePaidIfPending(Long payOrderId);

    /** 超时取消（WHERE id + status=待支付）。 */
    boolean cancelPending(Long payOrderId);

    /** 超时待支付单（PayTimeoutJob 取数）。 */
    List<PayOrder> listTimeout(LocalDateTime cutoff);

    /** 指定日期+渠道的支付成功流水（对账取数）。 */
    List<PayOrder> listPaidByDateAndChannel(LocalDate billDate, String channel);
}
