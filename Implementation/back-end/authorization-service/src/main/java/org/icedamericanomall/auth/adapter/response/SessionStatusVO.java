package org.icedamericanomall.auth.adapter.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.icedamericanomall.auth.app.dto.SessionCheckDTO;

/**
 * 会话状态响应（适配层 VO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusVO {

    /** accessToken 是否有效 */
    private Boolean valid;

    /** accessToken 剩余有效秒数（valid=false 时为 0） */
    private Long ttlSeconds;

    /** 空闲超时时间（分钟），前端可用于提示用户 */
    private Long idleTimeoutMinutes;

    /** 是否需要前端刷新 Token（TTL 小于阈值时） */
    private Boolean shouldRefresh;

    /** 剩余 TTL 小于该值时提示前端刷新（秒） */
    private static final long REFRESH_THRESHOLD_SECONDS = 300L;

    /** 无效会话 */
    public static SessionStatusVO invalid() {
        return SessionStatusVO.builder()
                .valid(false)
                .ttlSeconds(0L)
                .idleTimeoutMinutes(30L)
                .shouldRefresh(false)
                .build();
    }

    /**
     * 应用层结果 → 适配层 VO。
     *
     * @param sessionCheckDTO 会话状态
     * @return 会话状态 VO
     */
    public static SessionStatusVO from(SessionCheckDTO sessionCheckDTO) {
        boolean shouldRefresh = sessionCheckDTO.valid()
                && sessionCheckDTO.ttlSeconds() < REFRESH_THRESHOLD_SECONDS;
        return SessionStatusVO.builder()
                .valid(sessionCheckDTO.valid())
                .ttlSeconds(sessionCheckDTO.ttlSeconds())
                .idleTimeoutMinutes(sessionCheckDTO.idleTimeoutMinutes())
                .shouldRefresh(shouldRefresh)
                .build();
    }
}
