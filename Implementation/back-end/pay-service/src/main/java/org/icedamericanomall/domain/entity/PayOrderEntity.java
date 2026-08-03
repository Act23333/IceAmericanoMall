package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付单聚合根 — DDD 充血模型 V5.0
 *
 * <pre>
 * 状态机:
 *   0-PENDING_SUBMIT → 1-PENDING_PAY → 3-SUCCESS
 *                                     → 2-TIMEOUT_CANCELLED
 *
 * 不变量:
 *   1. 已成功的支付单不可再变更
 *   2. 回调必须验签后才 markSuccess
 * </pre>
 */
@Getter
@TableName("pay_order")
public class PayOrderEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int PENDING_SUBMIT     = 0;
    public static final int PENDING_PAY        = 1;
    public static final int TIMEOUT_CANCELLED  = 2;
    public static final int SUCCESS            = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizOrderNo;
    private String payOrderNo;
    private Long bizUserId;
    private String payChannelCode;
    private Integer amount;
    private Integer payType;
    private Integer status;
    private String expandJson;
    private String resultCode;
    private String resultMsg;
    private LocalDateTime paySuccessTime;
    private LocalDateTime payOverTime;
    private String qrCodeUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /** 标记支付成功（仅 PENDING_PAY → SUCCESS） */
    public void markSuccess(String thirdPartyResultCode) {
        if (this.status == SUCCESS) return; // 幂等
        assertStatus(PENDING_PAY, "只有待支付状态可标记成功");
        this.status = SUCCESS;
        this.resultCode = thirdPartyResultCode;
        this.paySuccessTime = LocalDateTime.now();
    }

    /** 超时取消 */
    public void cancelTimeout() {
        if (this.status == SUCCESS) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已支付订单不可取消");
        }
        this.status = TIMEOUT_CANCELLED;
    }

    /** 余额支付直接成单（跳过 PENDING_PAY） */
    public void markBalancePaid() {
        this.status = SUCCESS;
        this.paySuccessTime = LocalDateTime.now();
    }

    /** 设置为待支付状态（接收支付链接后） */
    public void markPending(String qrCodeUrl, LocalDateTime timeout) {
        assertStatus(PENDING_SUBMIT, "只有待提交状态可设置为待支付");
        this.status = PENDING_PAY;
        this.qrCodeUrl = qrCodeUrl;
        this.payOverTime = timeout;
    }

    // ==================== 查询方法 ====================

    public boolean isSuccess()   { return this.status == SUCCESS; }
    public boolean isPending()   { return this.status == PENDING_PAY; }
    public boolean isCancelled() { return this.status == TIMEOUT_CANCELLED; }
    public boolean isTimeout(LocalDateTime now) {
        return this.status == PENDING_PAY && this.payOverTime != null && now.isAfter(this.payOverTime);
    }

    // ==================== 内部校验 ====================

    private void assertStatus(int expected, String msg) {
        if (this.status != expected)
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, msg + "，当前状态: " + this.status);
    }

    // ==================== 持久层 setter (仅 MyBatis 使用) ====================

    public void setId(Long v) { this.id = v; }
    public void setBizOrderNo(String v) { this.bizOrderNo = v; }
    public void setPayOrderNo(String v) { this.payOrderNo = v; }
    public void setBizUserId(Long v) { this.bizUserId = v; }
    public void setPayChannelCode(String v) { this.payChannelCode = v; }
    public void setAmount(Integer v) { this.amount = v; }
    public void setPayType(Integer v) { this.payType = v; }
    public void setStatus(Integer v) { this.status = v; }
    public void setExpandJson(String v) { this.expandJson = v; }
    public void setResultCode(String v) { this.resultCode = v; }
    public void setResultMsg(String v) { this.resultMsg = v; }
    public void setPaySuccessTime(LocalDateTime v) { this.paySuccessTime = v; }
    public void setPayOverTime(LocalDateTime v) { this.payOverTime = v; }
    public void setQrCodeUrl(String v) { this.qrCodeUrl = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
