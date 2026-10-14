package org.icedamericanomall.user.infrastructure.persistence.oplog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.model.oplog.OperationLog;
import org.icedamericanomall.user.domain.repository.OperationLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 操作日志仓储实现（基础设施层）—— PO ↔ 领域模型转换在本类内联
 * （对齐 {@code MemberGrowthLogRepositoryImpl} 先例；ADR-032 管道二）。
 */
@Repository
@RequiredArgsConstructor
public class OperationLogRepositoryImpl implements OperationLogRepository {

    private final OperationLogMapper operationLogMapper;

    @Override
    public List<OperationLog> findPageDesc(int offset, int size) {
        return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLogPO>()
                        .orderByDesc(OperationLogPO::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(OperationLogRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return operationLogMapper.selectCount(null);
    }

    @Override
    public void save(OperationLog log) {
        operationLogMapper.insert(toPO(log));
    }

    // ==================== PO <-> 领域模型 ====================

    static OperationLog toDomain(OperationLogPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return OperationLog.builder()
                .id(dataObject.getId())
                .userId(dataObject.getUserId())
                .username(dataObject.getUsername())
                .action(dataObject.getAction())
                .target(dataObject.getTarget())
                .detail(dataObject.getDetail())
                .ip(dataObject.getIp())
                .createTime(dataObject.getCreateTime())
                .build();
    }

    static OperationLogPO toPO(OperationLog log) {
        if (log == null) {
            return null;
        }
        OperationLogPO dataObject = new OperationLogPO();
        dataObject.setId(log.getId());
        dataObject.setUserId(log.getUserId());
        dataObject.setUsername(log.getUsername());
        dataObject.setAction(log.getAction());
        dataObject.setTarget(log.getTarget());
        dataObject.setDetail(log.getDetail());
        dataObject.setIp(log.getIp());
        dataObject.setCreateTime(log.getCreateTime());
        return dataObject;
    }
}
