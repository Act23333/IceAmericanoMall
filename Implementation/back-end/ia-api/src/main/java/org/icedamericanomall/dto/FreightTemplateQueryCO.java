package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 运费模板批量查询请求 CO（F2 运费模型，trade-service → item-service，I43）。
 *
 * <p>对应契约 {@code 05-API-Specification.md} §4.14.17
 * {@code POST /internal/item/freight-template/query}：
 * {@code { sellerId(Long, 必填), templateIds(List<Long>, 必填, 可空数组=仅按 sellerId 查) }}。
 *
 * <p><b>{@code sellerId} 口径</b>：商家 {@code user_id}（与 {@code product.seller_id} 同口径，
 * ADR-019 Phase A），不是 {@code seller.id}。
 *
 * <p><b>{@code templateIds} 语义</b>：订单内商品绑定的模板 ID 集合；允许为空数组（或 null，
 * 等价于空数组）——此时仅按 {@code sellerId} 返回该商家全部启用模板，供调用方解析默认模板。
 */
@Data
public class FreightTemplateQueryCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家 ID（= 商家 user_id）。必填。 */
    private Long sellerId;

    /** 待查模板 ID 集合；空/null = 仅按 sellerId 返回全部启用模板。 */
    private List<Long> templateIds;
}
