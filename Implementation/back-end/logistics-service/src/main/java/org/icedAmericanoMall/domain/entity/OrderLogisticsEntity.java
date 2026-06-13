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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
