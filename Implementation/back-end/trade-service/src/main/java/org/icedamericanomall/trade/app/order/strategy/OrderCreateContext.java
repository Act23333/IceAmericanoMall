package org.icedamericanomall.trade.app.order.strategy;

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * V4.1: 订单创建上下文（京东/淘宝 OrderContext 模式）。
 * 承载所有订单创建所需数据，在策略执行过程中被逐步填充。
 */
@Data
public class OrderCreateContext {

    // === 基础信息 ===
    private Long userId;
    private OrderTypeEnum orderType;

    // === 购物车下单 ===
    private List<CartItemCO> cartItems;

    // === 立即购买 / 秒杀 ===
    /** 目标 SKU 业务键（32-hex）—— 线格式入参口径（ADR-061 §决策 2），不再是技术键。 */
    private String skuId;
    private Integer quantity;

    // === 秒杀专有（由 marketing-service Feign 调用传入） ===
    private Long flashId;
    private Integer flashPrice;   // 秒杀价(分)

    // === 拼团专有（F13, T27/ADR-049） ===
    private Long activityId;      // 拼团活动 id
    private String teamNo;        // 团号（空 = 开团，非空 = 参团）
    private Integer groupPrice;   // 拼团价(分)（I52 validate 返回后填充）

    // === 通用 ===
    private Long addressId;
    /** 买家留言（V5.7，可选，≤200）—— 随 {@code buildBaseOrder} 快照进订单聚合。 */
    private String remark;
    private Long userCouponId;            // @Deprecated V4.3: 单券，保留兼容
    private List<Long> userCouponIds;     // V4.3: 多券支持
    private AddressCO address;

    // === 多币种展示快照（F16 V5.17，契约 §4.6.1/§4.14.3b I61） ===
    /**
     * 展示币种（可选，ISO 4217）—— 前端请求透传；由 {@code OrderDisplaySnapshotService}
     * 决定去留：快照成功后与 {@link #displayRate} 一起随 {@code buildBaseOrder} 落
     * {@code orders.display_*} 两列，未传/CNY/降级（fail-open）时归空（两列 NULL）。
     */
    private String displayCurrency;
    /**
     * 下单时点定点整数汇率快照（每 1 CNY 兑目标币主单位数 × 10⁶）—— 仅快照成功后回填，
     * {@code null} = 未快照。展示参考、非结算参数，不参与金额不变式 V2。
     */
    private Long displayRate;

    // === 计算结果（策略 buildOrder 填充） ===
    private List<CartItemSnapshot> snapshots;
    private Long sellerId;
    private int totalAmount;
    private int discountAmount;

    // === 幂等锚点与补偿事实（V5.19 券核销资损修复） ===

    /**
     * 本次下单的统一订单号 —— 由编排层 {@code OrderCreationManager} 在<b>任何远程副作用之前</b>
     * 生成（策略 {@code buildBaseOrder} 原样采用），是券核销的幂等锚点，也是补偿与兜底对账
     * 在「订单对象已随异常丢失」时定位该单的唯一依据。
     */
    private String orderNo;

    /**
     * 「本次下单是否已发生券核销」的<b>事实标记</b> —— 由 {@code PricingService} 在
     * {@code couponClient.useCoupon(...)} <b>正常返回的那一刻</b>置位（不是"算出的金额"）。
     *
     * <p><b>为什么必须用副作用事实而非金额</b>：券核销是 marketing 侧独立事务
     * （{@code CouponUsageManager#useCoupon} 自带 {@code @Transactional}，调用返回即已提交），
     * 其抵扣额可能为 0（降级回调口径），也完全可能发生在
     * {@link #discountAmount} 回填<b>之前</b>——运费解析（I43，fail-closed）失败即落在该窗口。
     * 若补偿侧以 {@code discountAmount > 0} 判定，会漏判 → 用户券被静默吞掉（资损）。
     */
    private boolean couponConsumed;

    /**
     * 发生券核销的订单号（去重、按发生顺序）—— 与 {@link #couponConsumed} 同时置位，
     * 供补偿侧 {@code rollbackByOrderNo} 精确定位；多券下单复用同一订单号，故恒为单元素。
     */
    private List<String> couponConsumedOrderNos = new ArrayList<>();

    /**
     * 记录一次「券核销已发生」的事实（核销 Feign 正常返回的那一刻调用，幂等）。
     *
     * @param consumedOrderNo 该次核销使用的订单号（可空 —— 空即依赖 {@link #orderNo} 兜底）
     */
    public void markCouponConsumed(String consumedOrderNo) {
        this.couponConsumed = true;
        if (consumedOrderNo != null && !consumedOrderNo.isBlank()
                && !this.couponConsumedOrderNos.contains(consumedOrderNo)) {
            this.couponConsumedOrderNos.add(consumedOrderNo);
        }
    }

    /** 本次下单是否请求使用优惠券（单券兼容字段或多券列表任一非空）—— 兜底对账标记的写入判据。 */
    public boolean hasCouponRequested() {
        return (userCouponIds != null && !userCouponIds.isEmpty()) || userCouponId != null;
    }

    /**
     * 绑定收货地址快照，并**校验归属**（越权防护唯一出口）。
     *
     * <p>为什么必须校验：{@code addressId} 由前端传入，user-service 的
     * {@code /internal/address/{id}} 是集群内部端点、**不校验归属**；若不在此校验，
     * 买家可传入他人 addressId，把他人收货人姓名/手机号/详细地址快照进自己的订单后读取
     * （等价于 {@code GET /api/user/address/{id}} 的 IDOR）。
     *
     * <p>归属依据 {@link AddressCO#getUserId()}（user-service 侧 {@code user_id} NOT NULL，
     * 生产必非空）；不匹配一律拒绝。
     *
     * @param address 由 {@code AddressClient.getAddress} 取回的地址快照
     * @throws BizException 地址不存在或不属于当前下单用户
     */
    public void bindAddress(AddressCO address) {
        if (address == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "收货地址不存在");
        }
        if (!Objects.equals(userId, address.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "收货地址不属于当前用户");
        }
        this.address = address;
    }

    /**
     * 下单上下文中的商品快照（策略装配 → 订单项）+ 计价依据。
     *
     * <p><b>标识口径（ADR-061 §决策 1/2）</b>：{@link #skuId} / {@link #spuId} 均为
     * <b>业务键</b> 32-hex {@code String}（来源 {@code SkuCO.skuId}/{@code SkuCO.spuId}）——
     * 它们既写进 {@code order_item} 的标识列（跨域追溯/比价锚点），也是 I22 券范围
     * {@code spu_ids} 的取值（客户端按 {@code scope_values} 与商品业务键预过滤才能命中）。
     *
     * <p>{@link #categoryId}/{@link #sellerId} 保持技术键：它们是同库内部外键，
     * 券范围校验与运费计价都在服务端完成，不出线格式（A 案，ADR-061 §决策 4）。
     */
    @Data
    public static class CartItemSnapshot {
        /** SKU 业务键（{@code sku.sku_id} 的 32-hex 形态）。 */
        private String skuId;
        /** SPU 业务键（{@code spu.spu_id} 的 32-hex 形态）—— 券 scope 校验/订单项快照锚点。 */
        private String spuId;
        private Long categoryId;   // V4.3: 优惠券 scope 校验用（同库技术键，不出线格式）
        private Long sellerId;
        /** SPU 名（原 {@code productName}，ADR-061 §决策 5）。 */
        private String spuName;
        private String skuSpec;
        private Integer price;
        private Integer quantity;
        private String image;
        /** V5.6（F2）：商品绑定的运费模板 ID（可为 null = 未绑定 → 计价回落商家默认模板）。 */
        private Long freightTemplateId;
    }
}
