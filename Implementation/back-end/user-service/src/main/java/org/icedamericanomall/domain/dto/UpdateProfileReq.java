package org.icedamericanomall.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 用户资料更新请求 — Java Record。
 * updateMask 显式声明要更新的字段：["nickname", "avatar"]。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileReq(
        String nickname,
        String avatar,
        List<String> updateMask
) {}
