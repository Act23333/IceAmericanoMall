package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付单实体 —— 持久化模型（{@code pay_order} 表）。
 *
 * <pre>
 * 状态机 (状态码统一见 {@link org.icedamericanomall.pay.domain.enums.PayStatusEnum}):
 *   1-PENDING_PAY → 3-SUCCESS
 *                 ↘ 2-TIMEOUT_CANCEL
 *
 * 不变量:
 *   1. 已成功的支付单不可再变更
 *   2. 回调必须验签后，方可由领域服务以条件更新置成功
 *
 * 说明: 状态迁移由 PayOrderService 以「条件更新」实现（WHERE 带期望状态），
 * 本类不再自带状态机行为方法，避免持久化模型与状态码双轨。
 * </pre>
 */
@Getter
@TableName("pay_order")
public class PayOrderPO implements Serializable {

    private static final long serialVersionUID = 1L;

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
