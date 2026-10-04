package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.userprofile.UserProfileInfo;

/**
 * 用户画像查询端口（领域层定义，基础设施层用 Feign 实现）。
 */
public interface UserProfilePort {

    /**
     * 拉取当前登录用户的浏览历史与收藏（身份由请求头透传，无需入参）。
     *
     * @param historySize  浏览历史条数
     * @param favoriteSize 收藏条数
     * @return 用户画像；无数据时各列表为空（不返回 null）
     */
    UserProfileInfo loadProfile(int historySize, int favoriteSize);
}
