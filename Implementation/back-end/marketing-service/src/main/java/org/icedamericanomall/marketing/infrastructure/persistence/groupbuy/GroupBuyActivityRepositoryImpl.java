package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.repository.GroupBuyActivityRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 拼团活动仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 *
 * <p>M15「进行中」口径按时间窗推导（{@code start_time ≤ now ≤ end_time}），
 * 不叠加落库 {@code status} 快照条件（快照可能停留在创建时刻的「未开始」态，
 * 时间窗才是状态权威口径，复用 PromotionRepositoryImpl 先例）；命中索引 {@code idx_status}。
 */
@Repository
@RequiredArgsConstructor
public class GroupBuyActivityRepositoryImpl implements GroupBuyActivityRepository {

    private final GroupBuyActivityMapper groupBuyActivityMapper;

    @Override
    public IPage<GroupBuyActivity> findActiveActivities(LocalDateTime now, int pageNum, int pageSize) {
        return groupBuyActivityMapper.selectPage(new Page<>(pageNum, pageSize),
                        new LambdaQueryWrapper<GroupBuyActivityPO>()
                                .le(GroupBuyActivityPO::getStartTime, now)
                                .ge(GroupBuyActivityPO::getEndTime, now)
                                .orderByDesc(GroupBuyActivityPO::getId))
                .convert(GroupBuyActivityRepositoryImpl::toDomain);
    }

    @Override
    public GroupBuyActivity findById(Long id) {
        return toDomain(groupBuyActivityMapper.selectById(id));
    }

    @Override
    public void save(GroupBuyActivity activity) {
        GroupBuyActivityPO po = toPO(activity);
        groupBuyActivityMapper.insert(po);
        activity.setId(po.getId());
    }

    @Override
    public void update(GroupBuyActivity activity) {
        groupBuyActivityMapper.updateById(toPO(activity));
    }

    @Override
    public void deleteById(Long id) {
        groupBuyActivityMapper.deleteById(id);
    }

    // ==================== PO <-> 聚合 ====================

    static GroupBuyActivity toDomain(GroupBuyActivityPO po) {
        if (po == null) {
            return null;
        }
        return GroupBuyActivity.builder()
                .id(po.getId())
                .name(po.getName())
                .sellerId(po.getSellerId())
                .spuId(po.getSpuId())
                .skuId(po.getSkuId())
                .groupPrice(po.getGroupPrice())
                .requiredMembers(po.getRequiredMembers())
                .timeLimitHours(po.getTimeLimitHours())
                .startTime(po.getStartTime())
                .endTime(po.getEndTime())
                .status(po.getStatus())
                .version(po.getVersion())
                .createTime(po.getCreateTime())
                .build();
    }

    static GroupBuyActivityPO toPO(GroupBuyActivity activity) {
        GroupBuyActivityPO po = new GroupBuyActivityPO();
        po.setId(activity.getId());
        po.setName(activity.getName());
        po.setSellerId(activity.getSellerId());
        po.setSpuId(activity.getSpuId());
        po.setSkuId(activity.getSkuId());
        po.setGroupPrice(activity.getGroupPrice());
        po.setRequiredMembers(activity.getRequiredMembers());
        po.setTimeLimitHours(activity.getTimeLimitHours());
        po.setStartTime(activity.getStartTime());
        po.setEndTime(activity.getEndTime());
        po.setStatus(activity.getStatus());
        po.setVersion(activity.getVersion());
        po.setCreateTime(activity.getCreateTime());
        return po;
    }
}
