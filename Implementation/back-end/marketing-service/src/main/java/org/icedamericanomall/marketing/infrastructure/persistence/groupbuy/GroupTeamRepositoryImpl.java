package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.marketing.domain.enums.GroupTeamStatusEnum;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;
import org.icedamericanomall.marketing.domain.repository.GroupTeamRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拼团团仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 *
 * <p>成团状态机的<b>人数条件推进语义全部写在本类</b>（契约性不变式归 SQL）：
 * 参团 {@code +1 WHERE status=1 AND current_members < required_members}、
 * 满员 {@code 1→2 WHERE current_members >= required_members}、
 * 过期结团 {@code 1→3}；命中索引 {@code idx_activity_status_expire}。
 */
@Repository
@RequiredArgsConstructor
public class GroupTeamRepositoryImpl implements GroupTeamRepository {

    private final GroupTeamMapper groupTeamMapper;

    @Override
    public GroupTeam findById(Long id) {
        return toDomain(groupTeamMapper.selectById(id));
    }

    @Override
    public GroupTeam findByTeamNo(String teamNo) {
        return toDomain(groupTeamMapper.selectOne(new LambdaQueryWrapper<GroupTeamPO>()
                .eq(GroupTeamPO::getTeamNo, IdGenerator.toBytesOrNull(teamNo))));
    }

    @Override
    public List<GroupTeam> findTeamsByActivityAndStatus(Long activityId, int status, int limit) {
        return groupTeamMapper.selectList(new LambdaQueryWrapper<GroupTeamPO>()
                        .eq(GroupTeamPO::getActivityId, activityId)
                        .eq(GroupTeamPO::getStatus, status)
                        .orderByDesc(GroupTeamPO::getId)
                        .last("LIMIT " + limit))
                .stream().map(GroupTeamRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<GroupTeam> listExpiredTeams(LocalDateTime now, int limit) {
        return groupTeamMapper.selectList(new LambdaQueryWrapper<GroupTeamPO>()
                        .eq(GroupTeamPO::getStatus, GroupTeamStatusEnum.ONGOING.getCode())
                        .lt(GroupTeamPO::getExpireTime, now)
                        .orderByAsc(GroupTeamPO::getExpireTime)
                        .last("LIMIT " + limit))
                .stream().map(GroupTeamRepositoryImpl::toDomain).toList();
    }

    @Override
    public void save(GroupTeam team) {
        GroupTeamPO po = toPO(team);
        groupTeamMapper.insert(po);
        team.setId(po.getId());
    }

    /** 参团条件更新 +1：仅拼团中且未满才生效（0 行 = 团满/已结，参团失败的竞态兜底）。 */
    @Override
    public int incrementCurrentMembers(Long teamId) {
        return groupTeamMapper.update(null, new LambdaUpdateWrapper<GroupTeamPO>()
                .setSql("current_members = current_members + 1")
                .eq(GroupTeamPO::getId, teamId)
                .eq(GroupTeamPO::getStatus, GroupTeamStatusEnum.ONGOING.getCode())
                .apply("current_members < required_members"));
    }

    /** 满员条件置已成团（1→2，附 success_time）：仅当前人数已达标才生效，幂等空操作安全。 */
    @Override
    public int markTeamSuccessIfFull(Long teamId) {
        return groupTeamMapper.update(null, new LambdaUpdateWrapper<GroupTeamPO>()
                .set(GroupTeamPO::getStatus, GroupTeamStatusEnum.SUCCEEDED.getCode())
                .set(GroupTeamPO::getSuccessTime, LocalDateTime.now())
                .eq(GroupTeamPO::getId, teamId)
                .eq(GroupTeamPO::getStatus, GroupTeamStatusEnum.ONGOING.getCode())
                .apply("current_members >= required_members"));
    }

    /** 团状态条件迁移（乐观语义：仅当前状态等于 fromStatus 才更新；过期结团即 1→3）。 */
    @Override
    public int updateTeamStatusConditional(Long teamId, int fromStatus, int toStatus) {
        return groupTeamMapper.update(null, new LambdaUpdateWrapper<GroupTeamPO>()
                .set(GroupTeamPO::getStatus, toStatus)
                .eq(GroupTeamPO::getId, teamId)
                .eq(GroupTeamPO::getStatus, fromStatus));
    }

    // ==================== PO <-> 聚合 ====================

    static GroupTeam toDomain(GroupTeamPO po) {
        if (po == null) {
            return null;
        }
        return GroupTeam.builder()
                .id(po.getId())
                .teamNo(IdGenerator.toHexString(po.getTeamNo()))
                .activityId(po.getActivityId())
                .leaderUserId(po.getLeaderUserId())
                .requiredMembers(po.getRequiredMembers())
                .currentMembers(po.getCurrentMembers())
                .status(po.getStatus())
                .expireTime(po.getExpireTime())
                .successTime(po.getSuccessTime())
                .createTime(po.getCreateTime())
                .build();
    }

    static GroupTeamPO toPO(GroupTeam team) {
        GroupTeamPO po = new GroupTeamPO();
        po.setId(team.getId());
        po.setTeamNo(IdGenerator.toBytes(team.getTeamNo()));
        po.setActivityId(team.getActivityId());
        po.setLeaderUserId(team.getLeaderUserId());
        po.setRequiredMembers(team.getRequiredMembers());
        po.setCurrentMembers(team.getCurrentMembers());
        po.setStatus(team.getStatus());
        po.setExpireTime(team.getExpireTime());
        po.setSuccessTime(team.getSuccessTime());
        po.setCreateTime(team.getCreateTime());
        return po;
    }
}
