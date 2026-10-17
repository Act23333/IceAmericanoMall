package org.icedamericanomall.item.adapter.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UpdateSpuReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * SPU 名 —— 请求体键 {@code spu_name}（P4 商家编辑；契约 §1.8④）。
     * <p><b>O2 裁决（2026-10-18）：写侧与读出侧同批收口</b>（原 {@code name}）；
     * 局部更新语义：{@code null} = 本次不改名。
     */
    private String spuName;
    private String mainImage;
    private String description;
    private String brand;
    private Long categoryId;

    /**
     * 绑定的运费模板 ID（F2 运费模型，V5.6）。
     * <p>本请求为<b>局部更新</b>语义：{@code null} = 本次不改动绑定关系；
     * 非空时必须归属当前商家，否则 {@code FREIGHT_TEMPLATE_NOT_FOUND(3041)}。
     */
    private Long freightTemplateId;

    // V4.0: 商品级销售标签
    private String salesTags;
}
