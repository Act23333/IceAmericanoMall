package org.icedamericanomall.merchant.domain.model.shop;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * 店铺状态（领域枚举，纯 POJO）。
 *
 * <p>取值与 {@code shop.status} 列一致：{@code 0}=审核中，{@code 1}=正常，{@code 2}=冻结。
 * 与 {@link org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum} 同为 0/1/2，但语义独立：商家状态约束"能否营业"，
 * 店铺状态约束"该店铺对外是否可见"（商家正常 ≠ 每个店铺都正常）。
 */
public enum ShopStatusEnum {

    PENDING(0, "审核中"),
    NORMAL(1, "正常"),
    FROZEN(2, "已冻结"),
    ;

    private final Integer code;
    private final String description;

    ShopStatusEnum(Integer code, String description) {
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
    public static ShopStatusEnum fromCode(Integer value) {
        if (value == null) {
            return null;
        }
        for (ShopStatusEnum status : values()) {
            if (status.code.equals(value)) {
                return status;
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "无效的店铺状态: " + value);
    }
}
