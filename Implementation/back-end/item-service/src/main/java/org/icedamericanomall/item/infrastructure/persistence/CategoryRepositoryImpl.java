package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.model.category.Category;
import org.icedamericanomall.item.domain.repository.CategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分类仓储实现 —— MyBatis-Plus 访问 {@code category} 表，PO ↔ 聚合转换内聚于此。
 */
@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> findAllOrderBySort() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryPO>()
                        .orderByAsc(CategoryPO::getSortOrder))
                .stream()
                .map(CategoryRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Category findById(Long id) {
        CategoryPO po = categoryMapper.selectById(id);
        return po == null ? null : toDomain(po);
    }

    @Override
    public Category save(Category category) {
        CategoryPO po = toPO(category);
        categoryMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public void update(Category category) {
        categoryMapper.updateById(toPO(category));
    }

    @Override
    public void deleteById(Long id) {
        categoryMapper.deleteById(id);
    }

    private static Category toDomain(CategoryPO po) {
        return Category.builder()
                .id(po.getId())
                .name(po.getName())
                .parentId(po.getParentId())
                .level(po.getLevel())
                .sortOrder(po.getSortOrder())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private static CategoryPO toPO(Category category) {
        CategoryPO po = new CategoryPO();
        po.setId(category.getId());
        po.setName(category.getName());
        po.setParentId(category.getParentId());
        po.setLevel(category.getLevel());
        po.setSortOrder(category.getSortOrder());
        return po;
    }
}
