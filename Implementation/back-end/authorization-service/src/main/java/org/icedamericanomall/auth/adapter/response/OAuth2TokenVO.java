package org.icedamericanomall.auth.adapter.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.auth.app.dto.TokenPair;

/**
 * OAuth2 令牌响应（适配层 VO）。
 */
@Data
@Builder
public class OAuth2TokenVO {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    @JsonProperty("expires_in")
    private Long expiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    /**
     * 应用层结果 → 适配层 VO。
     *
     * @param tokenPair Token 对
     * @return OAuth2 令牌响应 VO
     */
    public static OAuth2TokenVO from(TokenPair tokenPair) {
        return OAuth2TokenVO.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .expiresIn(tokenPair.expiresIn())
                .build();
    }
}
