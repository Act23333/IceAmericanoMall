package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberGrowthLog;
import org.icedamericanomall.user.domain.repository.MemberGrowthLogRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 会员成长值流水仓储实现（基础设施层）。
 *
 * <p>业务键 = {@code orderNo}（{@code uk_order_no}）：定位一律走该唯一键；并发撞键时
 * {@link org.springframework.dao.DuplicateKeyException} 原样上抛（由应用层捕获回落，
 * ADR-035 先例）。PO ↔ 聚合转换为内联静态 {@link #toDomain} / {@link #toPO}。
 */
@Repository
@RequiredArgsConstructor
public class MemberGrowthLogRepositoryImpl implements MemberGrowthLogRepository {

    private final MemberGrowthLogMapper memberGrowthLogMapper;

    @Override
    public Optional<MemberGrowthLog> findByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return Optional.empty();
        }
        // FOR UPDATE 锁定读：并发回落路径须读到胜者已提交行（REPEATABLE READ 快照读不可见），
        // 语义见端口 Javadoc（ADR-044 I46 幂等）
        return Optional.ofNullable(toDomain(memberGrowthLogMapper.selectOne(
                new LambdaQueryWrapper<MemberGrowthLogPO>()
                        .eq(MemberGrowthLogPO::getOrderNo, IdGenerator.toBytes(orderNo))
                        .last("FOR UPDATE"))));
    }

    @Override
    public void insert(MemberGrowthLog growthLog) {
        memberGrowthLogMapper.insert(toPO(growthLog));
    }

    @Override
    public long sumGrowthByUserId(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return memberGrowthLogMapper.sumGrowthByUserId(userId);
    }

    /**
     * PO → 聚合。
     *
     * @param dataObject 持久化对象（可为 null）
     * @return 聚合；afterLevel code 无法归一化时该字段为 null（数据问题由读方兜底口径覆盖）
     */
    static MemberGrowthLog toDomain(MemberGrowthLogPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return MemberGrowthLog.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .orderNo(IdGenerator.toHexString(dataObject.getOrderNo()))
                .growthAmount(dataObject.getGrowthAmount())
                .afterLevel(MemberLevelEnum.fromCode(dataObject.getAfterLevel()))
                .createTime(dataObject.getCreateTime())
                .build();
    }

    /**
     * 聚合 → PO。
     *
     * @param growthLog 聚合（可为 null）
     * @return 持久化对象（输入为 null 时返回 null）
     */
    static MemberGrowthLogPO toPO(MemberGrowthLog growthLog) {
        if (growthLog == null) {
            return null;
        }
        MemberGrowthLogPO dataObject = new MemberGrowthLogPO();
        dataObject.setId(growthLog.getId());
        dataObject.setUserId(growthLog.getUserId());
        dataObject.setOrderNo(IdGenerator.toBytes(growthLog.getOrderNo()));
        dataObject.setGrowthAmount(growthLog.getGrowthAmount());
        dataObject.setAfterLevel(growthLog.getAfterLevel() == null ? null : growthLog.getAfterLevel().getCode());
        return dataObject;
    }
}
