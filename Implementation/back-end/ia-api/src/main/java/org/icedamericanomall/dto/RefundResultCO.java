package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 退款发起结果（跨服务契约对象）—— pay-service → trade-service（I40 响应，原始类型不包 {@code Result}）。
 *
 * <p>契约见 {@code 05-API-Specification.md} §4.14.15：v1 返回 {@code {refundNo, status}} 子集
 * （{@code RefundVO} 的字段子集），trade 侧据此记录退款单号用于后续排障/重试定位。
 */
@Data
public class RefundResultCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退款单号（业务唯一键，UUID）。 */
    private String refundNo;

    /** 退款状态：0-处理中 / 1-成功 / 2-失败。 */
    private Integer status;
}
