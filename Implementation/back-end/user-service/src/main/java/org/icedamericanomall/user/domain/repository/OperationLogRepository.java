package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.oplog.OperationLog;

import java.util.List;

/**
 * 操作日志仓储（领域端口）—— 审计台账读写，ADR-032 管道二。
 *
 * <p>分页遵循本服务域端口既有约定（{@code offset}/{@code size} + 独立计数），
 * 域层不引入 MyBatis-Plus 分页类型（ArchUnit 架构守卫：domain 零框架依赖，ADR-038/052）。
 */
public interface OperationLogRepository {

    /** 按创建时间倒序分页。 */
    List<OperationLog> findPageDesc(int offset, int size);

    /** 台账总数。 */
    long countAll();

    /** 写入一条审计记录（{@code createTime} 交 DB 默认值）。 */
    void save(OperationLog log);
}
