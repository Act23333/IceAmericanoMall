package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.entity.PointsLogEntity;

public interface PointsService extends IService<PointsLogEntity> {

    /** 添加积分（签到/下单/任务），返回当前总余额 */
    long addPoints(Long userId, int points, int type, String source);

    /** 查询积分总余额 */
    long getBalance(Long userId);

    /** 积分明细分页 */
    IPage<PointsLogEntity> getHistory(Long userId, int page, int size);
}
