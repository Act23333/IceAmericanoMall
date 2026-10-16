package org.icedamericanomall.user.adapter.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 浏览足迹视图对象（商品详情 + 浏览时间）。
 *
 * <p>字段取自 {@code spu_view_log} 联表商品读模型的查询列
 * （见 {@code HistoryViewQueryImpl}）：商品业务键 / 商品名 / 主图 / 品牌 / 最低价 / 浏览时间。
 * JSON 键名沿用历史的 snake_case（迁移前为 {@code Map<String,Object>} 直出），
 * 故以 {@link JsonProperty} 显式固定，避免改动前端报文契约。
 *
 * <p><b>标识口径（ADR-058 §1 / ADR-061 §决策 2）</b>：{@code spuId} 为 <b>SPU 业务键</b>
 * 32-hex {@code String}（与 {@code FavoriteVO.spu_id} 同型同义，可直接回链商品页）；
 * {@code id} 仍是足迹行主键（§1.8 例外条款，保持现状）。
 *
 * <p><b>关于 {@code product_name}</b>：本 VO 的该键由 B1 批次的契约清单排在
 * 「`spu_name` 名称收口」之外（14 处清单不含足迹端点），且本批未获授权改键名，
 * 故<b>保持现状</b>（已在交付报告中登记为残留项，避免静默扩范围）。
 */
@Data
public class HistoryVO {

    /** 足迹记录 ID（spu_view_log.id） */
    private Long id;

    /** 商品业务键（{@code spu.spu_id} 的 32-hex 形态） */
    @JsonProperty("spu_id")
    private String spuId;

    /** 浏览时间 */
    @JsonProperty("view_time")
    private LocalDateTime viewTime;

    /** 商品名称（SPU 名） */
    @JsonProperty("product_name")
    private String productName;

    /** 商品主图 */
    @JsonProperty("main_image")
    private String mainImage;

    /** 品牌 */
    private String brand;

    /** 商品最低价（分；无在售 SKU 时为空） */
    private Integer price;

    /** 联表读模型行 → 视图对象。 */
    public static HistoryVO from(Map<String, Object> row) {
        HistoryVO vo = new HistoryVO();
        vo.setId(toLong(row.get("id")));
        vo.setSpuId((String) row.get("spu_business_id"));
        vo.setViewTime(toLocalDateTime(row.get("view_time")));
        vo.setProductName((String) row.get("product_name"));
        vo.setMainImage((String) row.get("main_image"));
        vo.setBrand((String) row.get("brand"));
        vo.setPrice(toInteger(row.get("price")));
        return vo;
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime dateTime -> dateTime;
            case java.sql.Timestamp timestamp -> timestamp.toLocalDateTime();
            default -> throw new IllegalArgumentException("非法的浏览时间类型: " + value.getClass());
        };
    }
}
