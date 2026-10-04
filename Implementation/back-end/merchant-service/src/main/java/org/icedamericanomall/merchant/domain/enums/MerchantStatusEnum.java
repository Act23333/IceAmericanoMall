package org.icedamericanomall.merchant.domain.enums;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 商家状态（领域枚举，纯 POJO）。
 *
 * <p>取值与 {@code seller.status} 列一致：{@code 0}=审核中，{@code 1}=正常，{@code 2}=冻结。
 */
public enum MerchantStatusEnum {

    PENDING(0, "审核中"),
    NORMAL(1, "正常"),
    FROZEN(2, "已冻结"),
    ;

    private final Integer code;
    private final String description;

    MerchantStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 从状态码还原（null 安全）。 */
    public static MerchantStatusEnum fromCode(Integer value) {
        if (value == null) {
            return null;
        }
        for (MerchantStatusEnum status : values()) {
            if (status.code.equals(value)) {
                return status;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "无效的商家状态: " + value);
    }
}
