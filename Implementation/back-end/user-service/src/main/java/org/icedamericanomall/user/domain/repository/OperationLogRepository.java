package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.oplog.OperationLog;

import java.time.LocalDateTime;
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

    /**
     * 取窗口内指定 {@code action} 的记录（按创建时间倒序，最多 {@code limit} 条）—— 补偿对账的候选集。
     *
     * <p>只按 {@code action} 过滤，<b>不解析 detail</b>（人读文案随时会改）。
     *
     * @param action 台账 action 全值（{@code MODULE:ACTION}）
     * @param since  时间窗下界（含）
     * @param limit  单轮上限
     * @return 记录列表（无则空列表）
     */
    List<OperationLog> findByActionSince(String action, LocalDateTime since, int limit);

    /**
     * 是否存在「同 {@code action} + 同 {@code target}」且创建时间不早于 {@code since} 的记录 ——
     * 判定某条禁用是否已有<b>终结标记</b>（三种终结理由共用一个 action，理由只在 detail 里）。
     *
     * @param action 台账 action 全值
     * @param target 目标业务键（{@code target} 列原值）
     * @param since  时间下界（含），取该条 Disable 记录的创建时间
     * @return 存在返回 {@code true}
     */
    boolean existsByActionAndTargetSince(String action, String target, LocalDateTime since);
}
