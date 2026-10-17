package org.icedamericanomall.pay.domain.model.payorder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.PayStatusEnum;

import java.time.LocalDateTime;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * 支付单聚合根（纯 POJO，零框架依赖）—— {@code pay_order} 表的领域模型。
 *
 * <pre>
 * 状态机 (状态码统一见 {@link PayStatusEnum}):
 *   1-PENDING_PAY → 3-SUCCESS
 *                 ↘ 2-TIMEOUT_CANCEL
 *
 * 不变量:
 *   1. 已成功的支付单不可再变更
 *   2. 回调必须验签后，方可由仓储以条件更新置成功
 *   3. 支付单号（业务唯一键）在创建时生成，此后不可变
 *   4. 对外渠道交易号（channelTradeNo）与内部业务键彻底分离：回调/对账一律按它寻址，
 *      它跨重试稳定、库内唯一，且绝不等于 order_no / pay_order_no
 * </pre>
 *
 * <p><b>与持久化的分工</b>：本聚合持有<b>业务判断</b>（创建口径、状态谓词、置成功语义），
 * 状态迁移的<b>并发安全</b>由仓储的条件更新（{@code UPDATE ... WHERE status = 期望值}）保证 ——
 * 即「是否命中」由数据库回答，「命中后对象长什么样」由聚合回答。二者不可互换：
 * 聚合无法感知并发（可能已过期），仓储不做业务判断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrder {

    /** 支付超时窗口（分钟）：自创建/复用时刻起算，超时由 {@code PayTimeoutJob} 条件取消。 */
    public static final int PAY_TIMEOUT_MINUTES = 30;

    /**
     * 对外渠道交易号长度（位，hex）：在「12–20 位随机」口径内取 <b>20</b>。
     *
     * <p><b>为什么不是 32（UUIDv7 长度）</b>：微信 {@code out_trade_no} 的协议上限是 32 位
     * （见 {@code PaymentClient#MAX_OUT_TRADE_NO_LENGTH}），内部业务键正好占满 32 位即<b>零余量</b>
     * —— 任何前缀/拼接都直接超限、渠道拒单。20 位留 12 位余量。
     * <p><b>为什么不用 UUIDv7 截断</b>：v7 前 48 位是毫秒时间戳，截断后仍可从单号反推下单时刻
     * （对外单号会被渠道、对账文件、客服工单反复外传）；本值由 {@code IdGenerator#randomHex}
     * 全随机生成（80 bit 熵），无时间序、不可推断。
     */
    public static final int CHANNEL_TRADE_NO_LENGTH = 20;

    /** 技术主键（集群内部口径，不对外暴露）。 */
    private Long id;

    /** 业务订单号（trade-service 的 {@code orders.order_no}）。 */
    private String bizOrderNo;

    /** 支付单号（业务唯一键，32 位 UUID；<b>纯内部口径</b>，不下发给任何渠道）。 */
    private String payOrderNo;

    /**
     * 对外渠道交易号（{@code wechat out_trade_no} / {@code alipay out_trade_no} 的唯一取值来源）。
     *
     * <p>12–20 位随机 hex（默认 20），<b>与内部业务键彻底分离</b>：既不是 {@code order_no}
     * 也不是 {@code pay_order_no}。生命周期口径：
     * <ul>
     *   <li><b>创建时生成</b>（{@link #newPending}），落库后再调渠道 —— 渠道回调时按本列可查回支付单；</li>
     *   <li><b>跨重试稳定</b>：同一支付单重复发起（复用待支付行）<b>不重新生成</b>，
     *       否则渠道侧同一笔交易会出现两个商户单号；</li>
     *   <li><b>库内唯一</b>（{@code uk_channel_trade_no}）；</li>
     *   <li>余额渠道本地闭环、不对外发起，故为 {@code null}。</li>
     * </ul>
     */
    private String channelTradeNo;

    /** 支付用户 ID。 */
    private Long bizUserId;

    /** 支付渠道码：WECHAT / ALIPAY / BALANCE。 */
    private String payChannelCode;

    /** 支付金额（分）。 */
    private Integer amount;

    /** 支付方式（H5 / JSAPI / MINI / NATIVE / BALANCE）。 */
    private Integer payType;

    /** 支付状态：0-待提交 / 1-待支付 / 2-超时取消 / 3-成功。 */
    private Integer status;

    /** 扩展字段（JSON，暂未使用）。 */
    private String expandJson;

    /** 渠道返回码（回调置成功时回填渠道交易状态）。 */
    private String resultCode;

    /** 渠道返回信息。 */
    private String resultMsg;

    /** 支付成功时间。 */
    private LocalDateTime paySuccessTime;

    /** 支付超时时间（收银台倒计时口径）。 */
    private LocalDateTime payOverTime;

    /** 支付链接 / 二维码地址（余额支付为 {@code null}）。 */
    private String qrCodeUrl;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建「待支付」支付单（未落库状态，{@code id} 由仓储回填）。
     *
     * <p>创建口径内聚在此：支付单号（32 位 UUID 业务唯一键）、<b>对外渠道交易号</b>、
     * 初始状态（待支付）、超时窗口（{@value #PAY_TIMEOUT_MINUTES} 分钟）四者由本工厂一次性保证 ——
     * 微信/支付宝/余额三条发起路径均先落「待支付」行，口径必须一致。
     *
     * @param bizOrderNo  业务订单号
     * @param userId      支付用户 ID
     * @param amount      支付金额（分）
     * @param channelCode 支付渠道码
     * @param payType     支付方式码
     * @return 待支付支付单（未落库；支付链接由渠道下单后经仓储条件更新回填）
     */
    public static PayOrder newPending(String bizOrderNo, Long userId, int amount,
                                      String channelCode, int payType) {
        return PayOrder.builder()
                .bizOrderNo(bizOrderNo)
                .payOrderNo(newBusinessId())
                .channelTradeNo(newChannelTradeNo(channelCode))
                .bizUserId(userId)
                .payChannelCode(channelCode)
                .amount(amount)
                .payType(payType)
                .status(PayStatusEnum.PENDING_PAY.getCode())
                .payOverTime(LocalDateTime.now().plusMinutes(PAY_TIMEOUT_MINUTES))
                .build();
    }

    /** 生成业务唯一键（32 位 UUID，无连字符；与 {@code pay_order_no} 列对齐）。 */
    public static String newBusinessId() {
        return IdGenerator.nextId();
    }

    /**
     * 生成对外渠道交易号（{@value #CHANNEL_TRADE_NO_LENGTH} 位随机 hex，无时间序）。
     *
     * @param channelCode 支付渠道码；余额渠道本地闭环不对外发起 → 返回 {@code null}
     * @return 渠道交易号（渠道可空）
     */
    public static String newChannelTradeNo(String channelCode) {
        if (PayChannelEnum.BALANCE.getCode().equals(channelCode)) {
            return null;
        }
        return IdGenerator.randomHex(CHANNEL_TRADE_NO_LENGTH);
    }

    // ==================== 状态谓词 / 迁移 ====================

    /** 是否已支付成功（幂等判定的唯一口径：已成功不可重复发起、不可超时取消）。 */
    public boolean isPaid() {
        return status != null && status == PayStatusEnum.SUCCESS.getCode();
    }

    /** 是否处于「待支付」（复用支付单与条件更新的期望态）。 */
    public boolean isPendingPay() {
        return status != null && status == PayStatusEnum.PENDING_PAY.getCode();
    }

    /**
     * 置为支付成功（内存态迁移；数据库侧的并发安全由仓储条件更新保证）。
     *
     * @param paidAt 支付成功时间
     */
    public void markPaid(LocalDateTime paidAt) {
        this.status = PayStatusEnum.SUCCESS.getCode();
        this.paySuccessTime = paidAt;
    }

    /**
     * 应用「复用待支付单」的重置结果（仓储条件更新命中后回写内存态）。
     *
     * <p>渠道切换（如微信改支付宝）时金额、渠道、支付方式与超时时间必须整体刷新，
     * 否则收银台会展示上一渠道的二维码；超时时间随之顺延，避免刚复用即被超时任务关单。
     *
     * <p><b>两个字段刻意不动</b>：
     * <ul>
     *   <li>{@code channelTradeNo} —— 跨重试稳定，重复发起必须复用同一对外号（渠道侧不会出现两个商户单号）；</li>
     *   <li>{@code qrCodeUrl} —— 旧渠道的二维码对新渠道/新金额无效，先清空，由渠道下单后
     *       经仓储 {@code attachPayUrl} 条件回填。</li>
     * </ul>
     *
     * @param amount      新的应付金额（分）
     * @param channelCode 新的支付渠道码
     * @param payType     新的支付方式码
     * @param payOverTime 新的超时时间
     */
    public void applyPendingReset(int amount, String channelCode, int payType,
                                  LocalDateTime payOverTime) {
        this.amount = amount;
        this.payChannelCode = channelCode;
        this.payType = payType;
        this.payOverTime = payOverTime;
        this.qrCodeUrl = null;
    }

    /**
     * 应用「渠道下单返回支付链接」的结果（仓储条件更新命中后回写内存态）。
     *
     * @param payUrl 支付链接 / 二维码
     */
    public void applyPayUrl(String payUrl) {
        this.qrCodeUrl = payUrl;
    }
}
