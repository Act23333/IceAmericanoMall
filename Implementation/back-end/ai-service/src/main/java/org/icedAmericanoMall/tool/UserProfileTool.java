package org.icedAmericanoMall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.UserProfileClient;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V3.0.2: @Tool — 用户画像查询，供 Recommend Subagent 使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTool {

    private final UserProfileClient userProfileClient;

    @Tool("获取当前用户的浏览历史和收藏商品，用于个性化推荐")
    public String getUserProfile() {
        try {
            var userInfo = UserContext.getUser();
            if (userInfo == null) return "（未登录，无法获取个性化信息）";
            Long userId = userInfo.userId();

            Result<List<String>> history = userProfileClient.getBrowseHistory(10);
            Result<List<String>> favorites = userProfileClient.getFavorites(1, 10);

            StringBuilder sb = new StringBuilder("用户画像:\n");
            if (history != null && history.getData() != null && !history.getData().isEmpty()) {
                sb.append("最近浏览: ").append(String.join(", ", history.getData())).append("\n");
            }
            if (favorites != null && favorites.getData() != null && !favorites.getData().isEmpty()) {
                sb.append("收藏商品: ").append(String.join(", ", favorites.getData()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("UserProfileTool error: {}", e.getMessage());
            return "（无法获取用户画像）";
        }
    }
}
