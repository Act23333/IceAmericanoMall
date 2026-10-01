package org.icedamericanomall.user.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

/**
 * @ClassName: UserStatusEnum
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/30 14:50
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.user.constants
 */
public enum UserStatusEnum {

    FROZEN(0, "禁止使用"),
    NORMAL(1, "已激活"),
    ;
    @Getter
    @EnumValue
    final Integer status;
    final String description;

    UserStatusEnum(Integer status, String description) {
        this.status = status;
        this.description = description;
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
