package org.icedamericanomall.trade.adapter.request;

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 订单分页查询参数（{@code @QueryModel} 绑定；线上名 = snake_case，ADR-056）。
 *
 * <p>同一形状服务于买卖家与管理员三处订单分页：
 * 买家 {@code GET /api/trade/order/page}、商家 {@code GET /api/trade/seller/order/page}、
 * 管理员 {@code GET /api/trade/admin/orders/page}；归属过滤（userId/sellerId）不在请求体中，
 * 由登录态与端点语义决定（禁止前端传归属 ID 越权查询）。
 *
 * <p><b>筛选语义（A2 收口 —— AD12/T10 的 {@code keyword}/{@code start_date}/{@code end_date}）</b>：
 * <ul>
 *   <li>{@code keyword}（可空）：订单号<b>精确</b>、或收货人姓名/手机号<b>模糊</b>、
 *       或明细 SPU 名<b>模糊</b>（三选一命中即返回）。列名与 shipment 快照列同名，
 *       不改签名即可扩展；</li>
 *   <li>{@code start_date}/{@code end_date}（{@code yyyy-MM-dd}，可空）：
 *       下单时间区间，<b>双端闭区间</b>（{@code start_date} 取当日 00:00:00，
 *       {@code end_date} 取当日 23:59:59.999999999）；</li>
 *   <li>三者<b>后端实现</b>（此前契约已声明、实现未接线 → 参数被静默忽略，前端「筛选无效」）。
 *       格式非法 → 400 {@code PARAM_ERROR}（显式解析，避免 Spring 转换异常文案不可控）。</li>
 * </ul>
 *
 * <p>{@code page}/{@code size} 使用与 {@code @RequestParam(defaultValue=...)} 等价的字段初值，
 * 参数缺省时行为与迁移前完全一致。
 */
@Data
public class OrderPageReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单状态筛选，{@code null}=不限。 */
    private Integer status;

    /** 关键词（可空）：订单号精确 / 收货人姓名·手机号模糊 / 明细 SPU 名模糊。 */
    private String keyword;

    /** 下单起始日（可空，{@code yyyy-MM-dd}，含当日）。 */
    private String startDate;

    /** 下单截止日（可空，{@code yyyy-MM-dd}，含当日）。 */
    private String endDate;

    /** 页码（从 1 开始）。 */
    private int page = 1;

    /** 每页条数。 */
    private int size = 20;

    /**
     * 解析关键词语义：全空白归一为 {@code null}（= 不过滤），避免「传了空串即查不到」。
     *
     * @return 去空白后的关键词；无有效关键词时 {@code null}
     */
    public String parseKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * 解析下单起始时间（含当日 00:00:00）。
     *
     * @throws BadRequestException 格式非法 → 400 {@code PARAM_ERROR}
     */
    public LocalDateTime parseStartDateTime() {
        LocalDate date = parseDate(startDate, "startDate");
        return date == null ? null : date.atStartOfDay();
    }

    /**
     * 解析下单截止时间（含当日 23:59:59.999999999，双端闭区间）。
     *
     * @throws BadRequestException 格式非法 / 早于起始日 → 400 {@code PARAM_ERROR}
     */
    public LocalDateTime parseEndDateTime() {
        LocalDate date = parseDate(endDate, "endDate");
        if (date == null) {
            return null;
        }
        LocalDate start = parseDate(startDate, "startDate");
        if (start != null && date.isBefore(start)) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "endDate 不能早于 startDate");
        }
        return date.atTime(23, 59, 59, 999_999_999);
    }

    /** 显式解析 {@code yyyy-MM-dd}：缺失/空白 → {@code null}；格式非法 → 400。 */
    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    field + " 格式非法，应为 yyyy-MM-dd");
        }
    }
}
