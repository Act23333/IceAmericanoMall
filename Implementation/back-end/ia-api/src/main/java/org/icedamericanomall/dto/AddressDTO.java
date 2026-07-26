package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Address DTO for inter-service Feign calls (user-service → trade-service).
 */
@Data
public class AddressDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;
}
