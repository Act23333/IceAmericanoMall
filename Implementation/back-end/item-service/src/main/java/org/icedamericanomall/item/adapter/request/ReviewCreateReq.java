package org.icedamericanomall.item.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 创建商品评价请求 —— 替代 Controller 直接接收 Entity。userId 由登录态注入。
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code spuId} / {@code skuId} 均为
 * <b>SPU/SKU 业务键</b>（32-hex {@code String}，与 {@code SpuVO.spuId} / {@code SkuVO.skuId}
 * 同型同义）；应用层用同库只读索引解析为本域技术键后再落库（表结构不动）。
 */
@Data
public class ReviewCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    /** SPU 业务键（32-hex）—— 非空且非空白。 */
    @NotBlank(message = "商品ID不能为空")
    private String spuId;

    /** 关联订单号（32 位 hex，来源订单详情的 {@code OrderVO.orderNo}，ADR-050 三批契约）。 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** SKU 业务键（32-hex，可选）。 */
    private String skuId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容长度不能超过500")
    private String content;

    @Size(max = 500, message = "评价图片长度不能超过500")
    private String images;
}
