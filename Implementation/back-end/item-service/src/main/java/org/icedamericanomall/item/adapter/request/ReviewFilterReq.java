package org.icedamericanomall.item.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价筛选查询入参（GET 属性绑定，query 参数名与历史契约一致）。
 *
 * <p>字段与 query 参数一一对应：{@code rating?}、{@code hasMedia?}、{@code sort?}、{@code page}、{@code size}。
 * 未传参时保持默认值（page=1、size=10、sort=newest），由 Spring 数据绑定只覆盖请求中出现的字段。
 */
@Data
public class ReviewFilterReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 星级筛选：1~5，缺省不筛选。 */
    private Integer rating;

    /** 只看带图评价，缺省不筛选。 */
    private Boolean hasMedia;

    /** 排序：newest / highest / lowest，缺省 newest。 */
    private String sort = "newest";

    private Integer page = 1;

    private Integer size = 10;
}
