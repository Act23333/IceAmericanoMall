package org.icedamericanomall.item.domain.model.kg;

/**
 * 知识图谱图模型词汇常量（F15 Phase 1，PRD §16.4 定稿口径）—— 领域层零框架依赖。
 *
 * <p>节点/边类型/边来源的取值与 ia-api {@code KgEdgeItemCO} 契约注释、search-service
 * {@code kg_edges} 边索引投影三方对齐；item-service 侧统一从本类取值，禁止散落字面量。
 *
 * <p>Phase 1 边类型五类：BELONGS_TO / PARENT_OF / BRAND_OF / SOLD_BY（直建）+
 * PAIR_WITH（规则）；EXTRACTED 等 Phase 2 边类型不在此登记（契约未定稿）。
 */
public final class KgSchema {

    // ==================== 节点类型（srcType / dstType 取值） ====================

    /** 商品节点（真相源：{@code product} 表，节点标识 = 技术主键 id）。 */
    public static final String NODE_PRODUCT = "product";

    /** 类目节点（真相源：{@code category} 表，节点标识 = 技术主键 id）。 */
    public static final String NODE_CATEGORY = "category";

    /** 品牌值节点（真相源：{@code product.brand} 规范化字符串，无物理表，O4 裁决）。 */
    public static final String NODE_BRAND = "brand";

    /** 店铺/商家节点（真相源：{@code product.seller_id}，节点标识 = sellerId）。 */
    public static final String NODE_SELLER = "seller";

    // ==================== 边类型（relType 取值） ====================

    /** 属于类目：product → category（直建自 {@code product.category_id}）。 */
    public static final String REL_BELONGS_TO = "BELONGS_TO";

    /** 类目层级：category → category（直建自 {@code category.parent_id}，Phase 1 由 AD64 重建覆盖）。 */
    public static final String REL_PARENT_OF = "PARENT_OF";

    /** 品牌：product → brand（直建自 {@code product.brand} 规范化值节点）。 */
    public static final String REL_BRAND_OF = "BRAND_OF";

    /** 销售：product → seller（直建自 {@code product.seller_id}）。 */
    public static final String REL_SOLD_BY = "SOLD_BY";

    /** 搭配：category ↔ category（真相源 {@code pairing_rule}，运营人工配置）。 */
    public static final String REL_PAIR_WITH = "PAIR_WITH";

    // ==================== 边来源（source 取值） ====================

    /** 直建边（源表列即真相，可信度 100%）。 */
    public static final String SOURCE_DIRECT = "DIRECT";

    /** 规则边（运营配置，{@code pairing_rule}）。 */
    public static final String SOURCE_RULE = "RULE";

    // ==================== 边状态（status 取值） ====================

    /** 启用（邻居查询缺省仅返回启用边）。 */
    public static final int EDGE_ENABLED = 1;

    /** 停用（规则停用即时生效的落点：投影边保留但按 status 过滤）。 */
    public static final int EDGE_DISABLED = 0;

    private KgSchema() {
    }
}
