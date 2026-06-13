package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CreateOrderReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long addressId;

    @NotEmpty(message = "购物车项不能为空")
    private List<Long> cartItemIds;

    private String remark;
}
