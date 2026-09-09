package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("pay_order")
public class PayOrderEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizOrderNo;

    private String payOrderNo;

    private Long bizUserId;

    private String payChannelCode;

    /** 支付金额（分） */
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
}
