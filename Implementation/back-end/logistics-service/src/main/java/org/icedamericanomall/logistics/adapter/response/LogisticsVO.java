package org.icedamericanomall.logistics.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物流记录视图对象 —— Adapter 层出参，隔离持久化实体（{@code OrderLogisticsEntity} 不得出 Controller）。
 *
 * <p><b>V5.3 字段修剪</b>：移除审计字段 {@code updateTime} —— {@code createTime} 保留，
 * 它承载「发货入账时间」这一业务时间点。
 *
 * <p><b>V5.6（F3 拆单发货）</b>：新增 {@code packageNo} —— 一订单多包裹后
 * {@code GET /api/logistics/{orderId}} 返回<b>包裹列表</b>（按 {@code packageNo} 升序），
 * 与订单明细 {@code items[].packageNo} 对应（破坏性变更，见 §4.8.1）。
 */
@Data
public class LogisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID（一订单多包裹，按 {@code packageNo} 区分）。 */
    private Long orderId;

    /** 包裹号（同一订单从 1 递增，V5.6）。 */
    private Integer packageNo;

    /** 物流单号。 */
    private String logisticsNumber;

    /** 物流公司。 */
    private String logisticsCompany;

    /** 收件人快照。 */
    private String contact;

    /** 联系电话快照。 */
    private String mobile;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;

    /** 物流状态: 1-待揽收, 2-运输中, 3-已签收, 4-已退回。 */
    private Integer status;

    /** 发货入账时间。 */
    private LocalDateTime createTime;
}
