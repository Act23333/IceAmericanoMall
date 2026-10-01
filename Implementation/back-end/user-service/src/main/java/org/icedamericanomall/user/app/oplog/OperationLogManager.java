package org.icedamericanomall.user.app.oplog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.OperationLogVO;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogDO;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogMapper;
import org.springframework.stereotype.Component;

/**
 * 操作日志查询（应用层）—— 仅读；写入由 ia-common 的 OperationLogAspect 完成。
 */
@Component
@RequiredArgsConstructor
public class OperationLogManager {

    private final OperationLogMapper operationLogMapper;

    /** 管理后台：分页查询操作日志（按时间倒序）。 */
    public IPage<OperationLogVO> pageLogs(int pageNum, int size) {
        IPage<OperationLogDO> result = operationLogMapper.selectPage(new Page<>(pageNum, size),
                new LambdaQueryWrapper<OperationLogDO>()
                        .orderByDesc(OperationLogDO::getCreateTime));
        return result.convert(OperationLogVO::from);
    }
}
