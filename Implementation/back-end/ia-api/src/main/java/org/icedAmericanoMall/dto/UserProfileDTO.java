package org.icedAmericanoMall.dto;

import java.io.Serializable;
import java.util.List;

/** 用户画像 — Java Record。 */
public record UserProfileDTO(
        Long userId,
        String nickname,
        String avatar,
        List<String> browseHistory,
        List<String> favoriteProductIds
) implements Serializable {}
