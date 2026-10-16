package org.icedamericanomall.ai.domain.model.userprofile;

import java.util.List;

/**
 * 用户画像（领域 DTO）—— 个性化推荐输入；无数据时返回空列表而非 null。
 *
 * @param browseHistory      最近浏览商品标识
 * @param favoriteSpuIds 收藏商品标识
 */
public record UserProfileInfo(
        List<String> browseHistory,
        List<String> favoriteSpuIds
) {}
