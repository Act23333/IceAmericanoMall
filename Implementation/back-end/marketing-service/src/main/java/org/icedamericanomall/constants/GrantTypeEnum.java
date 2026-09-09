package org.icedamericanomall.constants;

import lombok.Getter;

/** V4.0: 优惠券获取方式枚举（京东标准：免费/付费/邀请/自动四分类） */
@Getter
public enum GrantTypeEnum {
    FREE_CLAIM(1, "免费领取"),
    PAID_PURCHASE(2, "付费购买"),
    INVITATION(3, "邀请制"),
    AUTO_ISSUE(4, "自动发放");

    private final int code;
    private final String desc;

    GrantTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
