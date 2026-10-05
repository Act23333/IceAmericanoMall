package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼团团视图对象（适配层出参）—— C 端 M16 团列表 / M17 团进度查询口径
 * （05-API-Specification §4.10.4）。
 *
 * <p><b>隐私口径</b>：不透出任何 userId（leaderUserId 等）——C 端团列表/详情只出
 * 进度事实，不含他人身份。{@code status}：1拼团中/2已成团/3已失败。
 */
@Data
public class GroupTeamVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 团号 */
    private String teamNo;
    /** 团状态：1拼团中/2已成团/3已失败 */
    private Integer status;
    /** 当前人数（含团长） */
    private Integer currentMembers;
    /** 成团人数 */
    private Integer requiredMembers;
    /** 成团截止时间 */
    private LocalDateTime expireTime;
    /** 成团时间（拼团中/已失败为 null） */
    private LocalDateTime successTime;
}
