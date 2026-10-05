package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 团状态回执（I53 join 返回，契约 §4.14.8c，V5.14）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTeamCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 团号（开团时为新团号） */
    private String teamNo;

    /** 团状态：1-拼团中 2-已成团 3-已失败 */
    private Integer status;

    /** 当前人数（含团长） */
    private Integer currentMembers;

    /** 成团人数 */
    private Integer requiredMembers;
}
