package org.icedamericanomall.item.domain.model.sku;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.item.domain.enums.HotReasonEnum;
import org.icedamericanomall.item.domain.enums.SkuStatusEnum;
import org.icedamericanomall.item.domain.enums.StockTypeEnum;

import java.time.LocalDateTime;
import org.icedamericanomall.common.utils.IdGenerator;

/**
 * SKU 实体（纯 POJO，零框架依赖）—— 归属 {@code Product} 聚合（商品下的一行规格）。
 *
 * <p><b>不变量</b>（由领域方法 + 仓储原子 SQL 共同保证）：
 * <ol>
 *   <li>{@code stock >= 0}：扣减走 {@code UPDATE ... WHERE stock >= ?} 单条原子 SQL
 *       （{@code SkuRepository}，ADR-020），受影响行数为 0 即库存不足；</li>
 *   <li>只有 {@link SkuStatusEnum#ON_SALE} 才能扣库存（原子 SQL 的 {@code WHERE status = 1}）；</li>
 *   <li>{@link #isUnlimited()} 的 SKU 不校验库存，扣减仅累加销量。</li>
 * </ol>
 *
 * <p><b>为何实体不再自带 setter 收口</b>：原实体用 {@code @Getter + 手写 setter} 表达
 * "只读"，但真正的写入方是 MyBatis 与仓储实现；四层化后写入路径收敛为
 * {@code SkuConverter}/{@code SkuRepositoryImpl}，领域方法（{@link #isUnlimited()}、
 * 工厂归一）承担规则，故直接使用 Lombok {@code @Data}。
 */
@Data
@Builder
public class Sku {

    /** 技术主键（外部契约 {@code skuId} 口径）。 */
    private Long id;

    /** 业务唯一键（UUID，32 位）。 */
    private String skuId;

    /** 所属商品技术主键（引用聚合根 {@code Product.id}）。 */
    private Long productId;

    /** 规格描述（如 "天青/标准"）。 */
    private String spec;

    /** 售价（分）。 */
    private Integer price;

    /** 划线原价（分）。 */
    private Integer originalPrice;

    /** 库存（限量 SKU 的有效值）。 */
    private Integer stock;

    private String image;

    private Integer soldCount;

    /** 库存类型（{@link StockTypeEnum} code）。 */
    private Integer stockType;

    private Boolean isHot;

    private String hotReason;

    private String salesTags;

    /** 状态（{@link SkuStatusEnum} code）。 */
    private Integer status;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 新建 SKU（商品发布时随聚合根一并落库）。
     *
     * <p>归一规则（与历史实现一致）：
     * <ul>
     *   <li>{@code stockType} 缺省回落 {@link StockTypeEnum#LIMITED}；</li>
     *   <li>热销原因按 {@link HotReasonEnum} 白名单归一，非法编码丢弃（置 null）而不阻断发布；</li>
     *   <li>非热销 SKU 只写 {@code isHot=false}，不写热销原因与销售标签。</li>
     * </ul>
     *
     * @param hotReason  原始热销原因编码（可能非法）
     * @param salesTags  热销标签（仅在热销时落库）
     * @return 新 SKU（未落库，{@code productId} = 聚合根技术主键）
     */
    public static Sku create(Long productId, String spec, Integer price, Integer stock, String image,
                             Integer stockType, Boolean isHot, String hotReason, String salesTags) {
        SkuBuilder builder = Sku.builder()
                .skuId(IdGenerator.nextId())
                .productId(productId)
                .spec(spec)
                .price(price)
                .stock(stock)
                .image(image)
                .soldCount(0)
                .stockType(stockType != null ? stockType : StockTypeEnum.LIMITED.getCode())
                .status(SkuStatusEnum.ON_SALE.getCode());
        boolean hot = Boolean.TRUE.equals(isHot);
        builder.isHot(hot);
        if (hot) {
            builder.hotReason(HotReasonEnum.normalize(hotReason)).salesTags(salesTags);
        }
        return builder.build();
    }

    // ==================== 查询 ====================

    /** 是否不限量 SKU（不校验库存，扣减仅累加销量）。 */
    public boolean isUnlimited() {
        return stockType != null && stockType == StockTypeEnum.UNLIMITED.getCode();
    }

    /** 是否可售。 */
    public boolean isOnSale() {
        return status != null && status == SkuStatusEnum.ON_SALE.getCode();
    }
}
