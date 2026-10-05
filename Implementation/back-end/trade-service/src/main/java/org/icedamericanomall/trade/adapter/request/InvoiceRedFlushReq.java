package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发票冲红入参（AD60 {@code PUT /api/admin/invoice/{invoiceNo}/red-flush}，契约 §4.13.8e）。
 *
 * <p>冲红原因必填（PRD US-INV-12），留痕于 {@code invoice.red_reason}；
 * 重复冲红 = 幂等空操作（服务端收敛，非报文职责）。
 */
@Data
public class InvoiceRedFlushReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 冲红原因（必填；换开场景填"换开"类原因，部分退款场景填处理说明）。 */
    @NotBlank(message = "冲红原因不能为空")
    @Size(max = 200, message = "冲红原因超长")
    private String reason;
}
