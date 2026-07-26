package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class OrderEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long sellerId;

    private Integer totalAmount;

    private Integer payAmount;

    private Integer discountAmount;

    private Integer status;

    private Integer paymentType;

    /** 收货人姓名快照 */
    private String receiverName;

    /** 收货人电话快照 */
    private String receiverPhone;

    /** 收货地址快照 */
    private String receiverAddress;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime consignTime;

    private LocalDateTime endTime;

    private LocalDateTime closeTime;

    private LocalDateTime commentTime;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
