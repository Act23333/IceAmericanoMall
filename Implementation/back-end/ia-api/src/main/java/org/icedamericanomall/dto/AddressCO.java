package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Address DTO for inter-service Feign calls (user-service → trade-service).
 */
@Data
public class AddressCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 地址归属用户 ID —— 供调用方做归属校验（下单快照必须校验，
     * 否则可传入他人 addressId 把他人 PII 快照进自己的订单）。
     *
     * <p>与 {@code AddressResp.userId} 同名对应；user-service 侧 {@code user_id} 为 NOT NULL，故生产必非空。
     */
    private Long userId;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;
}
