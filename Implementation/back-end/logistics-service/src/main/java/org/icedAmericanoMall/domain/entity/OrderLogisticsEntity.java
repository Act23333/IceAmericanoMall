package org.icedAmericanoMall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("order_logistics")
public class OrderLogisticsEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long orderId;

    private String logisticsNumber;

    private String logisticsCompany;

    private String contact;

    private String mobile;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;

    /**
     * 物流状态: 1-待揽收, 2-运输中, 3-已签收, 4-已退回
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
