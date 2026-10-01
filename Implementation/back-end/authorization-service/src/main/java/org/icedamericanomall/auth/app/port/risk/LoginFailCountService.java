package org.icedamericanomall.auth.app.port.risk;

/**
 * 登录失败计数能力（应用层端口，供风控评估与登录安全防护共用）。
 *
 * <p>风控维度的子端口：由基础设施层用 Redis 实现，是失败次数的<b>唯一数据源</b>
 * （避免各处自定义 key 造成计数不一致）。
 */
public interface LoginFailCountService {

    /** 读取当前失败次数（键不存在返回 0） */
    int getFailCount(String account);

    /** 累加一次失败（首次写入时设置 TTL） */
    void increment(String account);

    /** 清理失败计数（登录成功后调用） */
    void clear(String account);
}
