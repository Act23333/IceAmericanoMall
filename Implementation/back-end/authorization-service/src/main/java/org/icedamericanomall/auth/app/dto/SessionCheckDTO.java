package org.icedamericanomall.auth.app.dto;

import lombok.Builder;

/**
 * 会话状态查询结果（应用层出参，供适配层转换为 VO）。
 *
 * @param valid              accessToken 是否有效
 * @param ttlSeconds         accessToken 剩余有效秒数（无效时为 0）
 * @param idleTimeoutMinutes 空闲超时时间（分钟）
 */
@Builder
public record SessionCheckDTO(
        boolean valid,
        long ttlSeconds,
        long idleTimeoutMinutes
) {
}
