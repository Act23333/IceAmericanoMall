package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/** 浏览足迹服务 — V5.0 京东标准 (MySQL product_view_log + Redis 热缓存) */
public interface HistoryService {

    /** 记录浏览（同一商品重复浏览→更新view_time） */
    void record(Long userId, Long productId);

    /**
     * 分页查询足迹，返回商品详情+浏览时间
     * @param keyword 商品名称搜索（可选）
     */
    IPage<Map<String, Object>> page(Long userId, int page, int size,
                                     String keyword, String sort);

    /** 删除单条足迹 */
    void deleteById(Long userId, Long logId);

    /** 清空全部足迹 */
    void clear(Long userId);
}
