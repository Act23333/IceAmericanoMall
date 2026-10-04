package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 评价列表排序方式（京东/淘宝标准：最新 / 评分从高到低 / 评分从低到高）。
 *
 * <p>排序口径属领域读规则（同分时以创建时间兜底），故枚举落在领域层；
 * 非法编码回退 {@link #NEWEST}，保证脏入参不影响查询。
 */
@Getter
public enum ReviewSortEnum {

    NEWEST("newest", "最新"),
    HIGHEST("highest", "评分从高到低"),
    LOWEST("lowest", "评分从低到高");

    private final String code;
    private final String desc;

    ReviewSortEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按请求码解析，未知码回退 {@link #NEWEST}，避免非法入参影响排序。 */
    public static ReviewSortEnum fromCode(String code) {
        for (ReviewSortEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return NEWEST;
    }
}
