package org.icedamericanomall.marketing.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;

/**
 * 积分账本仓储（领域端口）—— 实现方：{@code infrastructure.persistence.points.PointsLogRepositoryImpl}。
 *
 * <p>单表流水：写为插入（原子），读为按 userId 聚合（CQRS 直查持久化读模型）。
 */
public interface PointsLogRepository {

    /** 追加一条流水（id 回填）。 */
    PointsLog save(PointsLog pointsLog);

    /** 积分总余额（数据库侧聚合，无流水用户返回 0）。 */
    long sumByUserId(Long userId);

    /** 积分明细分页（按创建时间倒序）。 */
    IPage<PointsLog> pageByUserId(Long userId, int pageNum, int pageSize);
}
