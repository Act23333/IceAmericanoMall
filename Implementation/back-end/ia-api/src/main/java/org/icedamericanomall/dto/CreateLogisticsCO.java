package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建物流记录 DTO — trade-service → logistics-service（{@code POST /internal/logistics/create}）。
 *
 * <p><b>拆单发货（F3，V5.6）</b>：{@code order_logistics} 由「一单一物流」升级为「一单多包裹」，
 * 本 CO 新增 {@code packageNo}——同一 {@code orderId + packageNo} 的重复发货事件幂等返回
 * （唯一键 {@code uk_order_package} 兜底）。{@code packageNo} 从 1 递增。
 */
@Data
public class CreateLogisticsCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    /** 包裹号（同一订单内从 1 递增；一包裹一条物流记录）。 */
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
}
