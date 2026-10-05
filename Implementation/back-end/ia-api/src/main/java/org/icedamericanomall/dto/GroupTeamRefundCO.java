package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 结团退款联动入参（marketing-service 结团后 → trade-service，契约 §4.14.16b I54，V5.14）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTeamRefundCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 团号（审计定位） */
    @NotBlank
    private String teamNo;

    /** 团成员订单号列表（marketing 侧成员表收集） */
    @NotBlank
    private List<String> orderNos;
}
