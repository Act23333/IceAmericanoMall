package org.icedamericanomall.item.domain.repository;

import org.icedamericanomall.item.domain.model.home.HomeConfig;

import java.util.List;
import java.util.Optional;

/**
 * 首页装修配置仓储（领域端口）—— 实现方：{@code infrastructure.persistence.HomeConfigRepositoryImpl}。
 */
public interface HomeConfigRepository {

    /** 启用中的配置（前台，sortOrder 升序）。 */
    List<HomeConfig> findEnabled();

    /** 全部配置（管理端，sortOrder 升序）。 */
    List<HomeConfig> findAll();

    /** 按 ID 载入。 */
    java.util.Optional<HomeConfig> findById(Long id);

    /** 新增（返回回填技术主键后的聚合）。 */
    HomeConfig save(HomeConfig config);

    /** 整字段覆盖更新（not_null 策略）。 */
    void update(HomeConfig config);

    /** 删除。 */
    void deleteById(Long id);
}
