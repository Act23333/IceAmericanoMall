package org.icedamericanomall.merchant.adapter.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 店铺装修保存请求（S1d：{@code PUT /api/seller/shops/{shopId}/decoration}，ADR-048）。
 *
 * <p>PUT 全量替换语义：未传字段按空保存（发送空列表即清空对应装修项）。
 * 条数上限在此快速失败（400 PARAM_ERROR）；URL 格式 / 去重 / 长度校验收口在
 * 领域 {@code ShopDecoration}（防御非适配层入口）。
 */
@Data
public class ShopDecorationReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 店铺公告（≤200 字，空白视为未设置） */
    @Size(max = 200, message = "公告最多 200 字")
    private String announcement;

    /** 轮播图 URL（≤5 条，每条须为 http(s) 链接） */
    @Size(max = 5, message = "轮播图最多 5 张")
    private List<String> banners;

    /**
     * 推荐商品位（SPU <b>业务键</b> 32-hex，≤8 个，去重）。
     *
     * <p>标识口径（ADR-062 §决策 3）：写侧与 {@code ShopDecorationVO} 读侧<b>同型同义</b>；
     * 形态校验（32-hex，非法即 400 {@code PARAM_ERROR} fail-fast）在领域工厂
     * {@code ShopDecoration.of} 内收口（适配层入参的唯一出口，ADR-062 §决策 7）。
     * 不做跨域存在性校验：ADR-048 Phase 1 口径（展示端过滤），且 ADR-057 明确
     * 「业务判断不得同库直读他域表」—— 需要校验时须走 item 域出口，属另批决策。
     */
    @Size(max = 8, message = "推荐商品位最多 8 个")
    private List<String> hotSpuIds;
}
