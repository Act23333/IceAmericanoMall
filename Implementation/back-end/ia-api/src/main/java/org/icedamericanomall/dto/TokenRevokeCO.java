package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户级令牌吊销 DTO —— 下游服务 → authorization-service（Feign，ADR-031）。
 *
 * <p>用于管理端「禁用/冻结用户」后立即下线该用户的全部登录态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenRevokeCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 目标用户技术主键 */
    private Long userId;

    /** 便捷构造（单字段请求体）。 */
    public static TokenRevokeCO of(Long userId) {
        return TokenRevokeCO.builder().userId(userId).build();
    }
}
