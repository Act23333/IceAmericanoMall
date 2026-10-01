package org.icedamericanomall.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.user.domain.entity.OperationLogEntity;
import org.icedamericanomall.user.domain.vo.OperationLogVO;

/**
 * 操作日志查询服务 —— 管理后台分页查看审计日志。
 */
public interface OperationLogService extends IService<OperationLogEntity> {

    IPage<OperationLogVO> pageLogs(int page, int size);
}
