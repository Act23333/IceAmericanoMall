package org.icedamericanomall.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话（Refresh Token）状态 —— 存放在 Redis 中的领域状态值。
 *
 * <p>保留可变 POJO 形态（而非 record）：需被 Redis 的 Jackson 序列化器反序列化，
 * 该序列化器依赖无参构造器 + setter。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String refreshToken;

    private String accessTokenJti;

    private Long userId;

    private String deviceId;

    private String deviceType;

    private PlatformEnum platform;

    /** 首次签发时间（毫秒） */
    private long issuedAt;

    /** 绝对过期时间（毫秒），轮换时继承，防止无限续期 */
    private long maxExpireAt;
}
