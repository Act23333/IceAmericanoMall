package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户级令牌吊销请求（内部端点，供 user-service 管理端禁用时调用，ADR-031）。
 *
 * <p>只承载目标用户技术主键；不承载调用方身份（服务间调用，生产环境网络隔离）。
 */
@Data
public class TokenRevokeReq {

    /** 目标用户技术主键 */
    @NotNull(message = "userId 不能为空")
    private Long userId;
}
