package org.icedamericanomall.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.convert.OperationLogConverter;
import org.icedamericanomall.user.domain.entity.OperationLogEntity;
import org.icedamericanomall.user.domain.vo.OperationLogVO;
import org.icedamericanomall.user.mapper.OperationLogMapper;
import org.icedamericanomall.user.service.OperationLogService;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLogEntity>
        implements OperationLogService {

    private final OperationLogConverter operationLogConverter;

    @Override
    public IPage<OperationLogVO> pageLogs(int page, int size) {
        IPage<OperationLogEntity> result = lambdaQuery()
                .orderByDesc(OperationLogEntity::getCreateTime)
                .page(new Page<>(page, size));
        return result.convert(operationLogConverter::toVO);
    }
}
