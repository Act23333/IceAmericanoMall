package org.icedamericanomall.user.adapter.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户资料更新请求 — Java Record（大厂标准 PATCH 语义）。
 *
 * <p>{@code updateMask} 显式声明要更新的字段：
 * {@code ["nickname", "avatar", "gender", "birthday"]}。
 * 未携带 updateMask 时按"非空字段全量合并"处理（历史语义）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateProfileReq(
        String nickname,
        String avatar,
        Integer gender,
        LocalDate birthday,
        List<String> updateMask
) {}
