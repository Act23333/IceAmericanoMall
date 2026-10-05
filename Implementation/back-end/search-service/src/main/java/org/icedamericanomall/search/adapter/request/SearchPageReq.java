package org.icedamericanomall.search.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品搜索查询入参（GET 属性绑定，query 参数名与历史契约一致）。
 *
 * <p>字段与 query 参数一一对应：{@code keyword?}、{@code categoryId?}、{@code brand?}、
 * {@code minPrice?}、{@code maxPrice?}、{@code sort?}（SE1 补齐）、{@code page}、{@code size}。
 * 未传参时保持默认值（page=1、size=20）。
 *
 * <p>SE6 facets 端点复用本类绑定，仅取 keyword/categoryId（page/size 被忽略）。
 */
@Data
public class SearchPageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;

    private Long categoryId;

    /** 品牌精确过滤（SE1，可空）。 */
    private String brand;

    /** 最低价（SE1，可空，分，含边界）。 */
    private Integer minPrice;

    /** 最高价（SE1，可空，分，含边界）。 */
    private Integer maxPrice;

    /** 排序 sales|price|newest|relevance（SE1，可空 = relevance）。 */
    private String sort;

    private Integer page = 1;

    private Integer size = 20;
}
