package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShipOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String logisticsNumber;
    private String logisticsCompany;
}
