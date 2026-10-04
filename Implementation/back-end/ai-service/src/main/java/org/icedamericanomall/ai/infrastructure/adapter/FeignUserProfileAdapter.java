package org.icedamericanomall.ai.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.userprofile.UserProfileInfo;
import org.icedamericanomall.ai.domain.port.UserProfilePort;
import org.icedamericanomall.client.UserProfileClient;
import org.icedamericanomall.common.api.Result;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link UserProfilePort} 的 Feign 实现（防腐层）——把 user-service 的列表报文转成本上下文领域对象。
 */
@Component
@RequiredArgsConstructor
public class FeignUserProfileAdapter implements UserProfilePort {

    /** 收藏查询固定取第 1 页（Agent 工具只展示摘要）。 */
    private static final int PAGE_NUM = 1;

    private final UserProfileClient userProfileClient;

    @Override
    public UserProfileInfo loadProfile(int historySize, int favoriteSize) {
        Result<List<String>> history = userProfileClient.getBrowseHistory(historySize);
        Result<List<String>> favorites = userProfileClient.getFavorites(PAGE_NUM, favoriteSize);
        return new UserProfileInfo(
                history != null && history.getData() != null ? history.getData() : List.of(),
                favorites != null && favorites.getData() != null ? favorites.getData() : List.of());
    }
}
