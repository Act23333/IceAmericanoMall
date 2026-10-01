package org.icedamericanomall.auth.domain.model;

/**
 * 跨上下文返回的用户身份（防腐层输出，隔离 user-service 的 DTO）。
 *
 * @param userId   user-service 的用户 ID
 * @param username 用户昵称（可能为空）
 */
public record UserIdentity(
        Long userId,
        String username
) {
}
