package org.icedamericanomall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenInfo implements Serializable {
    private String tokenId;
    private Long userId;
    private String username;
    private Long issuedAt;      // 首次签发时间（毫秒）
    private Long maxExpireAt;   // 绝对过期时间（毫秒）
    // 可选字段：deviceId, userAgent...
}