package org.icedamericanomall.auth.app.command;

import lombok.Builder;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;

/**
 * 注册命令 —— 应用层写操作入参（Adapter 层显式组装，不使用反射拷贝）。
 *
 * @param username 用户名（可选）
 * @param phone    手机号
 * @param password 密码明文
 * @param code     短信验证码
 * @param deviceId 设备标识
 * @param platform 平台类型（服务端识别）
 */
@Builder
public record RegisterCommand(
        String username,
        String phone,
        String password,
        String code,
        String deviceId,
        PlatformEnum platform
) {
}
