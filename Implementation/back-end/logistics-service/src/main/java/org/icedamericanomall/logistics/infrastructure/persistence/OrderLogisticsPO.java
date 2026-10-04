package org.icedamericanomall.logistics.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单物流 PO（{@code order_logistics} 表）。
 *
 * <p><b>V5.6 拆单发货（F3）</b>：本表由「一单一物流（order_id 即主键）」升级为
 * 「一单多包裹（一包裹一条记录）」——
 * <ul>
 *   <li>代理主键 {@code id BIGINT AUTO_INCREMENT}（收敛此前「主键复用 order_id」的约定偏差）；</li>
 *   <li>{@code order_id} 降为普通外键；包裹由 {@code package_no} 区分，唯一键
 *       {@code uk_order_package(order_id, package_no)}；</li>
 *   <li>{@code package_no} 从 1 递增，与订单明细 {@code order_item.package_no} 对应。</li>
 * </ul>
 */
@Data
@TableName("order_logistics")
public class OrderLogisticsPO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单 ID（一单多包裹）。 */
    private Long orderId;

    /** 包裹号（同一订单从 1 递增）。 */
    private Integer packageNo;

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
