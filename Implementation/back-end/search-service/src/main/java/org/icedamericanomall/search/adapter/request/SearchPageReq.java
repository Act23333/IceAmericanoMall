package org.icedamericanomall.search.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品搜索查询入参（GET 属性绑定，query 参数名与历史契约一致）。
 *
 * <p>字段与 query 参数一一对应：{@code keyword?}、{@code categoryId?}、{@code page}、{@code size}。
 * 未传参时保持默认值（page=1、size=20）。
 */
@Data
public class SearchPageReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;

    private Long categoryId;

    private Integer page = 1;

    private Integer size = 20;
}
