package org.icedAmericanoMall.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BadRequestException;

/**
 * @ClassName: UserStatusEnum
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/30 14:50
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.constants
 */
public enum UserStatusEnum {

    FROZEN(0, "禁止使用"),
    NORMAL(1, "已激活"),
    ;

    @EnumValue
    final Integer status;
    final String description;

    UserStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public Integer getCode() {
        return status;
    }

    public static UserStatusEnum of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }
        throw new BadRequestException(ErrorCode.USER_STATUS_ABNORMAL, "账户状态错误");
    }
}
