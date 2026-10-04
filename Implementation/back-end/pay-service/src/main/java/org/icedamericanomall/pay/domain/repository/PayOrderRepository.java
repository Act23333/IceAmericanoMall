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
 */
public interface PayOrderRepository {

    /** 按业务订单号取最新一条（防多行炸裂：历史可能有多条支付记录）。 */
    PayOrder getByBizOrderNo(String bizOrderNo);

    /** 按支付单号取（业务唯一键）。 */
    PayOrder getByPayOrderNo(String payOrderNo);

    /** 新增「待支付」支付单（创建口径由 {@link PayOrder#newPending} 内聚），返回回填 id 后的聚合。 */
    PayOrder createPending(String bizOrderNo, Long userId, int amount, String payUrl,
                           String channelCode, int payType);

    /**
     * 复用「待支付」单：条件更新金额/渠道/支付链接/超时时间（WHERE status=待支付）。
     *
     * @return 命中并应用重置后的聚合；未命中（状态已变更）返回 {@code null}
     */
    PayOrder resetPending(PayOrder existing, int amount, String channelCode, String payUrl, int payType);

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
