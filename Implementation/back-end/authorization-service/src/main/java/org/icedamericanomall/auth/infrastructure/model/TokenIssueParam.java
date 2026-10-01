package org.icedamericanomall.auth.infrastructure.model;

import lombok.Builder;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;

/**
 * 会话签发上下文（基础设施层内部模型）。
 *
 * <p>把"新建登录"与"轮换刷新"两种签发场景统一成一个入参，
 * 避免对外端口 {@code TokenGenerateParam} 暴露内部字段。
 *
 * @param userId      用户 ID
 * @param deviceType  设备类型（同端互斥的 Key 维度）
 * @param deviceId    设备唯一标识（可为空）
 * @param platform    平台
 * @param newLogin    true = 新建登录（重新计算绝对过期时间），false = 轮换刷新（继承原值）
 * @param maxExpireAt 绝对过期时间戳（毫秒），仅轮换刷新使用
 */
@Builder
public record TokenIssueParam(
        Long userId,
        String deviceType,
        String deviceId,
        PlatformEnum platform,
        boolean newLogin,
        long maxExpireAt
) {
}
