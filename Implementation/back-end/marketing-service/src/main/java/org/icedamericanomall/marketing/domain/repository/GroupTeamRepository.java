package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拼团团仓储（领域端口）—— 实现方：{@code infrastructure.persistence.groupbuy.GroupTeamRepositoryImpl}。
 *
 * <p>团以团号 {@code teamNo}（uk_team_no）为业务唯一键寻址；成团状态机的<b>人数条件推进</b>
 * 全部以条件更新承载（返回影响行数，0 行 = 条件不满足），调用方据行数判定成败：
 * <ul>
 *   <li>{@link #incrementCurrentMembers}：{@code +1 WHERE status=1 AND current_members < required}（0 行 = 团满/已结）；</li>
 *   <li>{@link #markTeamSuccessIfFull}：满员瞬间 {@code 1→2 WHERE current_members >= required}；</li>
 *   <li>{@link #updateTeamStatusConditional}：一般状态条件迁移（过期结团 {@code 1→3}）。</li>
 * </ul>
 */
public interface GroupTeamRepository {

    /** 按主键取团（不存在返回 null）。 */
    GroupTeam findById(Long id);

    /** 按团号取团（业务唯一键 uk_team_no；不存在返回 null）。 */
    GroupTeam findByTeamNo(String teamNo);

    /**
     * 某活动指定状态的团列表（M16 详情页「最近进行中团」）。
     *
     * @param activityId 拼团活动 id
     * @param status     团状态（{@code GroupTeamStatusEnum} code）
     * @param limit      条数上限（契约 M16 ≤10）
     * @return 团列表（按 id 降序 = 最新团优先；无则空集合）
     */
    List<GroupTeam> findTeamsByActivityAndStatus(Long activityId, int status, int limit);

    /**
     * 已过期的拼团中团列表（结团 Job 扫描：{@code status=1 AND expire_time < now}）。
     *
     * @param now   判定时刻
     * @param limit 单批扫描上限
     * @return 过期团列表（按 expire_time 升序 = 最早过期优先；无则空集合）
     */
    List<GroupTeam> listExpiredTeams(LocalDateTime now, int limit);

    /**
     * 新增团（INSERT；生成的主键回填到聚合，供开团用例落成员行）。
     *
     * @param team 待保存的团聚合（开团工厂产物）
     */
    void save(GroupTeam team);

    /**
     * 参团人数条件更新 +1：仅拼团中且未满才生效（参团竞态的 SQL 兜底）。
     *
     * @param teamId 团 id
     * @return 影响行数；0 = 团满/已结（调用方抛参团失败异常，事务回滚成员行）
     */
    int incrementCurrentMembers(Long teamId);

    /**
     * 满员条件置已成团（{@code 1→2}，附 success_time）：仅当前人数已达标才生效。
     *
     * @param teamId 团 id
     * @return 影响行数；0 = 未满或已不是拼团中（幂等空操作）
     */
    int markTeamSuccessIfFull(Long teamId);

    /**
     * 团状态条件迁移（乐观语义：仅当前状态等于 fromStatus 才更新；过期结团即 {@code 1→3}）。
     *
     * @param teamId      团 id
     * @param fromStatus  期望当前状态
     * @param toStatus    目标状态
     * @return 影响行数；0 = 状态已被其他执行方推进（幂等跳过）
     */
    int updateTeamStatusConditional(Long teamId, int fromStatus, int toStatus);
}
