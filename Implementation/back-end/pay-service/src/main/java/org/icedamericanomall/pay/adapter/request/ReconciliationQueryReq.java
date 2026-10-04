package org.icedamericanomall.pay.adapter.request;

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 对账查询/触发请求（适配层入参，{@code /api/admin/pay/reconciliation/**} 查询参数绑定）。
 *
 * <p>{@code billDate} 刻意以 {@link String} 承接、再由 {@link #parseBillDate(boolean)} 显式解析：
 * 直接绑定 {@link LocalDate} 时，格式/取值错误由 Spring 的类型转换抛
 * {@code MethodArgumentTypeMismatchException}，其错误文案不受控；显式解析可统一保证
 * 「缺失/格式非法 → 400 {@code PARAM_ERROR}(1101)」（契约 §4.13.14 AD46 的明确要求）。
 */
@Data
public class ReconciliationQueryReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（缺省 1）。 */
    private Integer page = 1;

    /** 每页条数（缺省 20）。 */
    private Integer size = 20;

    /** 对账渠道：{@code WECHAT} / {@code ALIPAY}（可空 = 不过滤）。 */
    private String channel;

    /** 批次状态（AD44，可空）：0-处理中 / 1-完成 / 2-失败。 */
    private Integer status;

    /** 账单日（{@code yyyy-MM-dd}；AD44 可空，AD46 必填）。 */
    private String billDate;

    /** 差异结果（AD45，可空）：1-匹配 / 2-渠道有本地无 / 3-本地有渠道无 / 4-金额不一致。 */
    private Integer result;

    /**
     * 解析账单日。
     *
     * @param required true = 缺失即拒绝（AD46）；false = 缺失返回 {@code null}（AD44 过滤条件）
     * @return 账单日；非必填且缺失时 {@code null}
     * @throws BadRequestException 缺失（required）或格式非法 → {@code PARAM_ERROR}(400)
     */
    public LocalDate parseBillDate(boolean required) {
        if (billDate == null || billDate.isBlank()) {
            if (required) {
                throw new BadRequestException(ErrorCode.PARAM_ERROR, "billDate 不能为空（格式 yyyy-MM-dd）");
            }
            return null;
        }
        try {
            return LocalDate.parse(billDate.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "billDate 格式非法，应为 yyyy-MM-dd");
        }
    }
}
