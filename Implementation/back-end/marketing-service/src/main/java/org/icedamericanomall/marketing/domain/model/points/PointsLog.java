package org.icedamericanomall.marketing.domain.model.points;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分账本条目聚合（纯 POJO）—— {@code points_log} 单表流水（无跨聚合不变量）。
 *
 * <p>type：1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期（历史口径不变）。写为插入（原子）、
 * 读为按 userId 聚合（CQRS 直查持久化读模型）。
 */
@Data
@Builder
public class PointsLog {

    private Long id;
    private Long userId;

    /** 积分变动（正=获得，负=消耗）。 */
    private Integer points;

    /** 类型：1=签到, 2=下单, 3=任务, 4=兑换消耗, 5=过期。 */
    private Integer type;

    /** 来源描述。 */
    private String source;

    private LocalDateTime createTime;

    /** 追加一条积分流水（id 由仓储回填）。 */
    public static PointsLog record(Long userId, int points, int type, String source) {
        return PointsLog.builder()
                .userId(userId)
                .points(points)
                .type(type)
                .source(source)
                .build();
    }
}
