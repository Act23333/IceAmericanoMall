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
        // MVP: only level-1 categories by default
        List<CategoryEntity> categories = lambdaQuery()
                .eq(CategoryEntity::getLevel, 1)
                .orderByAsc(CategoryEntity::getSortOrder)
                .list();
        return categories.stream()
                .map(categoryConverter::entityToTreeVO)
                .collect(Collectors.toList());
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
