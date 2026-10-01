package org.icedamericanomall.auth.adapter.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.auth.app.dto.LoginDTO;

/**
 * 登录响应（适配层 VO）。
 */
@Data
@Builder
public class LoginVO {

    // Token 相关
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    // 用户信息
    @JsonProperty("user_id")
    private Long userId;

    /**
     * 应用层结果 → 适配层 VO。
     *
     * @param loginDTO 登录结果
     * @return 登录响应 VO
     */
    public static LoginVO from(LoginDTO loginDTO) {
        return LoginVO.builder()
                .accessToken(loginDTO.accessToken())
                .expiresIn(loginDTO.expiresIn())
                .refreshToken(loginDTO.refreshToken())
                .userId(loginDTO.userId())
                .build();
    }
}
