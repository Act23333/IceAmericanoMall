package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeamMember;
import org.icedamericanomall.marketing.domain.repository.GroupTeamMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 拼团团成员仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 *
 * <p>插入撞 {@code uk_order_no}/{@code uk_team_user} 时由 MyBatis-Plus 异常翻译抛
 * Spring {@code DuplicateKeyException}，幂等回落（返回现状）由应用层处理，本实现不吞。
 */
@Repository
@RequiredArgsConstructor
public class GroupTeamMemberRepositoryImpl implements GroupTeamMemberRepository {

    private final GroupTeamMemberMapper groupTeamMemberMapper;

    @Override
    public void save(GroupTeamMember member) {
        GroupTeamMemberPO po = toPO(member);
        groupTeamMemberMapper.insert(po);
        member.setId(po.getId());
    }

    @Override
    public GroupTeamMember findByOrderNo(String orderNo) {
        return toDomain(groupTeamMemberMapper.selectOne(new LambdaQueryWrapper<GroupTeamMemberPO>()
                .eq(GroupTeamMemberPO::getOrderNo, orderNo)));
    }

    @Override
    public boolean existsByTeamIdAndUserId(Long teamId, Long userId) {
        return groupTeamMemberMapper.selectCount(new LambdaQueryWrapper<GroupTeamMemberPO>()
                .eq(GroupTeamMemberPO::getTeamId, teamId)
                .eq(GroupTeamMemberPO::getUserId, userId)) > 0;
    }

    @Override
    public List<GroupTeamMember> listByTeamId(Long teamId) {
        return groupTeamMemberMapper.selectList(new LambdaQueryWrapper<GroupTeamMemberPO>()
                        .eq(GroupTeamMemberPO::getTeamId, teamId)
                        .orderByAsc(GroupTeamMemberPO::getId))
                .stream().map(GroupTeamMemberRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    static GroupTeamMember toDomain(GroupTeamMemberPO po) {
        if (po == null) {
            return null;
        }
        return GroupTeamMember.builder()
                .id(po.getId())
                .teamId(po.getTeamId())
                .userId(po.getUserId())
                .orderNo(po.getOrderNo())
                .createTime(po.getCreateTime())
                .build();
    }

    static GroupTeamMemberPO toPO(GroupTeamMember member) {
        GroupTeamMemberPO po = new GroupTeamMemberPO();
        po.setId(member.getId());
        po.setTeamId(member.getTeamId());
        po.setUserId(member.getUserId());
        po.setOrderNo(member.getOrderNo());
        po.setCreateTime(member.getCreateTime());
        return po;
    }
}
