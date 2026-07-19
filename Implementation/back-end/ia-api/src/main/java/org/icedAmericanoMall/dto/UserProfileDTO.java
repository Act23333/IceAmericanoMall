package org.icedAmericanoMall.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * V3.0: 用户画像 Feign DTO — AI Recommend Subagent 使用。
 * 映射 user-service UserInfoResp + 浏览历史 + 收藏。
 */
@Data
public class UserProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String nickname;
    private String avatar;
    /** 浏览历史 (最近100条 productId) */
    private List<String> browseHistory;
    /** 收藏商品ID列表 */
    private List<String> favoriteProductIds;
}
