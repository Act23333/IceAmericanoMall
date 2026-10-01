package org.icedamericanomall.item.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.item.domain.entity.CategoryEntity;
import org.icedamericanomall.item.domain.vo.CategoryTreeVO;

import java.util.List;

public interface CategoryService extends IService<CategoryEntity> {

    List<CategoryTreeVO> getCategoryTree();

    void createCategory(CategoryEntity entity);

    void updateCategory(CategoryEntity entity);

    void deleteCategory(Long id);
}
