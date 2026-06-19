package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.convert.CategoryConverter;
import org.icedAmericanoMall.domain.entity.CategoryEntity;
import org.icedAmericanoMall.domain.vo.CategoryTreeVO;
import org.icedAmericanoMall.mapper.CategoryMapper;
import org.icedAmericanoMall.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, CategoryEntity> implements CategoryService {

    private final CategoryConverter categoryConverter;

    public CategoryServiceImpl(CategoryConverter categoryConverter) {
        this.categoryConverter = categoryConverter;
    }

    @Override
    public List<CategoryTreeVO> getCategoryTree() {
        // 查询所有分类，按 sort_order 排序
        List<CategoryEntity> allCategories = lambdaQuery()
                .orderByAsc(CategoryEntity::getSortOrder)
                .list();

        // 按 parentId 分组
        Map<Long, List<CategoryEntity>> childrenMap = allCategories.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CategoryEntity::getParentId));

        // 构建树：从根节点（parentId=null）开始递归
        return allCategories.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> buildTreeNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    private CategoryTreeVO buildTreeNode(CategoryEntity entity, Map<Long, List<CategoryEntity>> childrenMap) {
        CategoryTreeVO vo = categoryConverter.entityToTreeVO(entity);
        List<CategoryEntity> children = childrenMap.getOrDefault(entity.getId(), List.of());
        if (!children.isEmpty()) {
            vo.setChildren(children.stream()
                    .map(child -> buildTreeNode(child, childrenMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCategory(CategoryEntity entity) {
        save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryEntity entity) {
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        removeById(id);
    }
}
