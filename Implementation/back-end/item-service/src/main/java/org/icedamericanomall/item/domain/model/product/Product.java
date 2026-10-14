package org.icedamericanomall.item.domain.model.product;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.domain.enums.ProductStatusEnum;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 商品聚合根（纯 POJO，零框架依赖）—— 商品/SKU/分类/评价/首页配置/运费模板六个聚合之一。
 *
 * <p><b>聚合边界（小聚合原则）</b>：{@code Product} 是聚合根，其内部实体是 {@code Sku}
 * （同一商品下的规格行，随商品原子变更）。分类（{@code Category}）、运费模板
 * （{@code FreightTemplate}）是<b>独立聚合</b>——商品通过 {@code categoryId} /
 * {@code freightTemplateId} <b>按 ID 引用</b>，不持有对象引用；跨聚合协作
 * （发布商品时校验模板归属）上移到应用层编排。
 *
 * <p><b>双主键语义</b>：
 * <ul>
 *   <li>{@link #id} —— 技术主键（BIGINT），对外 HTTP 契约与跨服务 CO 的寻址口径</li>
 *   <li>{@link #productId} —— 业务唯一键（UUID，32 位），下单快照与跨服务日志口径</li>
 * </ul>
 *
 * <p>业务规则内聚在实体方法中（可见性断言、商家归属断言、PATCH 局部更新），不泄漏到应用层。
 */
@Data
@Builder
public class Product {

    /** 技术主键（外部契约口径；聚合装载后按 id 定位与更新属规范操作）。 */
    private Long id;

    /** 业务唯一键（UUID，32 位）。 */
    private String productId;

    /** 商家 ID（= 商家 user_id，ADR-019 Phase A）。 */
    private Long sellerId;

    /** 类目 ID（引用 {@code Category} 聚合，按 ID 引用）。 */
    private Long categoryId;

    /** 绑定的运费模板 ID（引用 {@code FreightTemplate} 聚合）；null = 未绑定 → 下单回落商家默认模板。 */
    private Long freightTemplateId;

    private String name;

    private String mainImage;

    /** JSON 多图 URL（历史保留列，写入口径不变）。 */
    private String images;

    /** 视频 URL（历史保留列）。 */
    private String videoUrl;

    /** JSON 规格参数（历史保留列）。 */
    private String attributes;

    /** JSON 售后标签（历史保留列）。 */
    private String serviceTags;

    /** JSON 销售标签。 */
    private String salesTags;

    /** 浏览次数（历史保留列）。 */
    private Integer viewCount;

    private String description;

    private String brand;

    private Integer soldCount;

    private Integer commentCount;

    /** 是否广告位：1=是（历史保留列，发布时不设置）。 */
    private Integer isAd;

    /** 状态（{@link ProductStatusEnum} code）。 */
    private Integer status;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 发布商品（未落库状态，{@code id} 由仓储落库时补齐）。
     *
     * <p>设置口径与历史实现完全一致：业务键 UUID、状态=上架、销量/评论数=0、发布时间=now；
     * {@code isAd} / {@code viewCount} 等历史列不设置（保持数据库默认）。
     *
     * @return 新商品聚合（未落库）
     */
    public static Product publish(Long sellerId, Long categoryId, Long freightTemplateId, String name,
                                  String mainImage, String description, String brand) {
        return Product.builder()
                .productId(newBusinessId())
                .sellerId(sellerId)
                .categoryId(categoryId)
                .freightTemplateId(freightTemplateId)
                .name(name)
                .mainImage(mainImage)
                .description(description)
                .brand(brand)
                .soldCount(0)
                .commentCount(0)
                .status(ProductStatusEnum.ON_SHELF.getCode())
                .publishTime(LocalDateTime.now())
                .build();
    }

    /** 生成业务唯一键（UUID 去连字符，32 位）。 */
    public static String newBusinessId() {
        return IdGenerator.nextId();
    }

    // ==================== 查询 ====================

    /** 是否逻辑删除。 */
    public boolean isDeleted() {
        return status != null && status == ProductStatusEnum.DELETED.getCode();
    }

    /** 是否归属指定商家。 */
    public boolean isOwnedBy(Long sellerId) {
        return Objects.equals(this.sellerId, sellerId);
    }

    // ==================== 业务断言 ====================

    /**
     * 断言商品对外可见（前台详情/列表取数前置校验）。
     *
     * @throws BizException 商品不存在或已删除（{@code PRODUCT_NOT_FOUND}）
     */
    public void assertVisible() {
        if (isDeleted()) {
            throw new BizException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    /**
     * 断言当前商家可编辑本商品（商家编辑自己的商品）。
     *
     * @throws BizException 非本商家商品（{@code FORBIDDEN}）
     */
    public void assertModifiableBy(Long sellerId) {
        if (!isOwnedBy(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权修改该商品");
        }
    }

    /**
     * 断言当前商家可回复本商品下的评价（只能回复自己店铺商品的评价）。
     *
     * @throws BizException 非本商家商品（{@code FORBIDDEN}）
     */
    public void assertReplyableBy(Long sellerId) {
        if (!isOwnedBy(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能回复自己店铺商品的评价");
        }
    }

    // ==================== 变更 ====================

    /** 上下架/状态迁移（状态码不做合法性收敛——与历史行为一致，直接落库）。 */
    public void changeStatus(Integer status) {
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * PATCH 局部更新（{@code null} = 本次不改动该字段，京东/淘宝 PATCH 语义）。
     *
     * <p>口径与历史实现一致：只覆盖 {@code name/mainImage/description/brand/categoryId/
     * freightTemplateId} 六个字段；{@code salesTags} 等历史请求字段<b>历史即不落库</b>，
     * 本次重构保持该行为不变（不静默"顺手修复"，避免接口行为漂移）。
     *
     * @param categoryId        类目 ID（仅当其与原值不同时才改动，保持引用一致性）
     * @param freightTemplateId 运费模板 ID（null = 不改动绑定关系）
     */
    public void applyPatch(String name, String mainImage, String description, String brand,
                           Long categoryId, Long freightTemplateId) {
        if (name != null) {
            this.name = name;
        }
        if (mainImage != null) {
            this.mainImage = mainImage;
        }
        if (description != null) {
            this.description = description;
        }
        if (brand != null) {
            this.brand = brand;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (freightTemplateId != null) {
            this.freightTemplateId = freightTemplateId;
        }
        this.updateTime = LocalDateTime.now();
    }

    /** 评论数 +1（发表评价时由应用层编排调用）。 */
    public void increaseCommentCount() {
        this.commentCount = (this.commentCount == null ? 0 : this.commentCount) + 1;
    }
}
