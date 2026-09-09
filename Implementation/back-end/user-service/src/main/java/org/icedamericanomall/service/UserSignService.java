package org.icedamericanomall.service;

/**
 * @ClassName: UserSignService
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/24 1:58
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.service
 */
public interface UserSignService {

    // 今日签到
    boolean sign(Long userId);
    // 查询今日是否签到
    boolean isTodaySigned(Long userId);
    // 统计当月签到次数
    long countCurrentMonthSign(Long userId);
    // 统计连续签到天数
    long countContinuousSign(Long userId);
}
