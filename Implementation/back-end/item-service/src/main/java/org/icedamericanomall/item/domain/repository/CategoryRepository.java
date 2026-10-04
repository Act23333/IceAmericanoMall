package org.icedamericanomall.item.domain.repository;

import org.icedamericanomall.item.domain.model.category.Category;

import java.util.List;

/**
 * 分类仓储（领域端口）—— 实现方：{@code infrastructure.persistence.CategoryRepositoryImpl}。
 */
public interface CategoryRepository {

    /** 全部分类（按 sortOrder 升序，树装配的原始数据）。 */
    List<Category> findAllOrderBySort();

    /** 按 ID 载入（可为 null）。 */
    Category findById(Long id);

    /** 新增（返回回填技术主键后的聚合）。 */
    Category save(Category category);

    /** 按 ID 覆盖更新（不存在则 0 行静默，历史口径）。 */
    void update(Category category);

    /** 删除。 */
    void deleteById(Long id);
}
