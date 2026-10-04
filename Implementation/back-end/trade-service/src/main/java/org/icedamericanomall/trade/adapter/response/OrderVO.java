package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象 —— 对外返回（买家/商家/管理员订单列表与详情共用）。
 *
 * <p><b>V5.3 字段修剪（对外契约收缩，供 API 文档同步）</b>：
 * <ul>
 *   <li>移除 {@code id}（{@code orders} 表技术主键）：对外身份一律用业务键 {@code orderNo}
 *       （详情/取消/确认/发货等端点全部以 orderNo 定位），技术主键不外泄（ADR-003 隔离原则）。</li>
 *   <li>移除 {@code paymentType}：全仓无任何写入路径为 {@code orders.payment_type} 赋值，
 *       恒为 null 的死字段。</li>
 * </ul>
 * 保留 {@code userId}/{@code sellerId}（管理端按人/店定位订单）、{@code orderType}
 * （区分 NORMAL/DIRECT/FLASH_SALE）、金额分单位字段、时间字段（列表/详情展示）。
 * 内部字段 {@code version}/{@code closeTime}/{@code commentTime}/{@code updateTime} 从未暴露。
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务订单号（对外唯一标识）。 */
    private String orderNo;

    /** 买家 ID（管理端按用户检索）。 */
    private Long userId;

    /** 商家 ID（管理端/商家端按店铺检索）。 */
    private Long sellerId;

    /** 订单原价合计（分）= 各订单项小计之和。 */
    private Integer totalAmount;

    /** 应付金额（分）= totalAmount - discountAmount + postFee（V5.6 运费模型，ADR-037）。 */
    private Integer payAmount;

    /** 优惠金额（分）。 */
    private Integer discountAmount;

    /**
     * 运费（分，V5.6 运费模型增量）—— 下单时按运费模板计算并快照；
     * 字段上线前报文不含该键，前端须按可空字段消费。
     */
    private Integer postFee;

    /** 状态码，见 {@link org.icedamericanomall.trade.domain.enums.OrderStatusEnum}。 */
    private Integer status;

    /** 订单类型：1=NORMAL, 2=DIRECT, 3=FLASH_SALE, 4=PRESALE（预售未实现）。 */
    private Integer orderType;

    /** 收货人快照。 */
    private String receiverName;

    /** 收货电话快照。 */
    private String receiverPhone;

    /** 收货地址快照（省市区街道门牌拼接）。 */
    private String receiverAddress;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime consignTime;

    private LocalDateTime endTime;

    /** 订单项（下单快照）；仅详情接口装配，列表接口为 {@code null}。 */
    private List<OrderItemVO> items;
}
