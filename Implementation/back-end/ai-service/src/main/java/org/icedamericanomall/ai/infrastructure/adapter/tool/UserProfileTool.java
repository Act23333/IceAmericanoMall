package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.userprofile.UserProfileInfo;
import org.icedamericanomall.ai.domain.port.UserProfilePort;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V3.0.2: @Tool — 用户画像查询，供 Recommend Subagent 使用。
 * <p>
 * 仅依赖领域端口 {@link UserProfilePort}，Feign 调用在基础设施层（防腐层）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileTool {

    /** 浏览历史条数。 */
    private static final int HISTORY_SIZE = 10;

    /** 收藏条数。 */
    private static final int FAVORITE_SIZE = 10;

    private final UserProfilePort userProfilePort;

    /**
     * 获取当前登录用户的浏览历史与收藏。
     *
     * @return 用户画像文本；未登录或拉取失败返回提示文案
     */
    @Tool("获取当前用户的浏览历史和收藏商品，用于个性化推荐")
    public String getUserProfile() {
        try {
            if (UserContext.getUser() == null) {
                return "（未登录，无法获取个性化信息）";
            }
            UserProfileInfo profile = userProfilePort.loadProfile(HISTORY_SIZE, FAVORITE_SIZE);
            StringBuilder sb = new StringBuilder("用户画像:\n");
            appendSection(sb, "最近浏览: ", profile.browseHistory());
            appendSection(sb, "收藏商品: ", profile.favoriteSpuIds());
            return sb.toString();
        } catch (Exception e) {
            log.warn("UserProfileTool error: {}", e.getMessage());
            return "（无法获取用户画像）";
        }
    }

    /** 仅在有数据时追加一段（避免输出空标题）。 */
    private static void appendSection(StringBuilder sb, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sb.append(label).append(String.join(", ", values)).append("\n");
        }
    }
}
