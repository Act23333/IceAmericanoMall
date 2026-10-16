package org.icedamericanomall.search.domain.model.search;

/**
 * 商品搜索命中（领域读模型）—— DB LIKE 与 ES 两条检索链的统一返回形状。
 *
 * <p>字段与对外 {@code SpuSearchVO} 一致（历史 JSON 契约不变）；
 * {@code price} 为在售 SKU 最低价（商品表无此字段，由检索实现补齐）。
 *
 * @param id          商品技术主键（即契约层的 spuId，I48 相似推荐的命中 id 与组装键）
 * @param spuId   商品业务ID
 * @param categoryId  分类ID
 * @param name        商品名
 * @param description 描述
 * @param brand       品牌
 * @param mainImage   主图
 * @param price       最低 SKU 价格（分）
 * @param soldCount   销量
 * @param score       相关度得分：向量检索为 ES kNN 得分（I48 相似度口径），关键词检索为 null
 */
public record SpuSearchHit(Long id, String spuId, Long categoryId, String name,
                               String description, String brand, String mainImage,
                               Integer price, Integer soldCount, Double score) {

    /**
     * 兼容构造（无得分场景：关键词检索 / DB LIKE 链）。
     */
    public SpuSearchHit(Long id, String spuId, Long categoryId, String name,
                            String description, String brand, String mainImage,
                            Integer price, Integer soldCount) {
        this(id, spuId, categoryId, name, description, brand, mainImage, price, soldCount, null);
    }
}
