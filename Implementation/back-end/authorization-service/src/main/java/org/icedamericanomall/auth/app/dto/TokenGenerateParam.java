package org.icedamericanomall.auth.app.dto;

import lombok.Builder;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;

import java.util.Map;

/**
 * Token 生成参数 —— {@code LoginTokenService} 端口的专用入参。
 * <p>不是领域实体，不是 DO，是应用层的值对象（跟随端口归属应用层）。
 */
@Builder
public record TokenGenerateParam(
        /** 用户 ID（必需） */
        Long userId,

        /** 平台类型（用于同端互斥） */
        PlatformEnum platform,

        /** 设备 ID（可选，用于同端多设备） */
        String deviceId,

        /** 客户端 IP（可选，记录登录日志） */
        String clientIp,

        /** 附加 claims（可选，如 tenantId、extra） */
        Map<String, Object> extraClaims
) {

    public TokenGenerateParam {
        extraClaims = extraClaims == null ? Map.of() : Map.copyOf(extraClaims);
    }

    public String deviceType() {
        return platform != null ? platform.name() : "UNKNOWN";
    }
}
