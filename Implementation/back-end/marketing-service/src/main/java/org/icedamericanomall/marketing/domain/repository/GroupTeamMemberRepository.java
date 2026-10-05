package org.icedamericanomall.marketing.domain.repository;

import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeamMember;

import java.util.List;

/**
 * 拼团团成员仓储（领域端口）—— 实现方：{@code infrastructure.persistence.groupbuy.GroupTeamMemberRepositoryImpl}。
 *
 * <p>成员行以 {@code uk_order_no}（一单一团一次）+ {@code uk_team_user}（同团不重复）双唯一键
 * 兜幂等（ADR-049）；插入撞键时抛 Spring {@code DuplicateKeyException}，幂等语义由应用层
 * 回落处理，本端口不吞。
 */
public interface GroupTeamMemberRepository {

    /**
     * 插入成员行（撞 uk_order_no / uk_team_user 抛 {@code DuplicateKeyException}）。
     *
     * @param member 待保存的成员聚合
     */
    void save(GroupTeamMember member);

    /** 按订单号取成员行（uk_order_no 幂等重放锚点；不存在返回 null）。 */
    GroupTeamMember findByOrderNo(String orderNo);

    /** 用户是否已在该团（uk_team_user 预检，防同团重复参团）。 */
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    /** 团的全部成员行（结团 Job 收集 orderNos 触发逐单退款；按 id 升序）。 */
    List<GroupTeamMember> listByTeamId(Long teamId);
}
