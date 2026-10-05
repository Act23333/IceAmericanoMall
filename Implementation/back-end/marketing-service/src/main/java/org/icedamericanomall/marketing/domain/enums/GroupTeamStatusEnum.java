package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 拼团团状态枚举（F13 拼团，V5.14，ADR-049）。
 *
 * <p>统一语言对齐：口径与 {@code group_team.status} 表注释、05-API-Specification §4.10.4/§4.14.8c
 * 一致 —— 1=拼团中、2=已成团、3=已失败。状态机推进规则：
 * <ul>
 *   <li>1→2：满员瞬间由仓储条件更新承载（{@code WHERE status=1 AND current_members >= required_members}）；</li>
 *   <li>1→3：超时未满员，由 {@code GroupBuyExpireJob} 条件置失败并联动退款（唯一失败路径，ADR-049 §4）。</li>
 * </ul>
 */
@Getter
public enum GroupTeamStatusEnum {
    ONGOING(1, "拼团中"),
    SUCCEEDED(2, "已成团"),
    FAILED(3, "已失败");

    private final int code;
    private final String desc;

    GroupTeamStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
