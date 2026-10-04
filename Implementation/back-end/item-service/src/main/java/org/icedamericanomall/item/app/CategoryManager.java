package org.icedamericanomall.item.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.adapter.assembler.CategoryConverter;
import org.icedamericanomall.item.adapter.request.CategoryReq;
import org.icedamericanomall.item.adapter.response.CategoryTreeVO;
import org.icedamericanomall.item.adapter.response.CategoryVO;
import org.icedamericanomall.item.domain.model.category.Category;
import org.icedamericanomall.item.domain.repository.CategoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类编排（应用层）—— 树装配（读模型）与 CRUD 事务边界。
 */
@Component
@RequiredArgsConstructor
public class CategoryManager {

    private final CategoryRepository categoryRepository;
    private final CategoryConverter categoryConverter;

    /** 分类树：全量载入后按 parentId 递归装配（读模型装配不入聚合）。 */
    public List<CategoryTreeVO> getCategoryTree() {
        List<Category> all = categoryRepository.findAllOrderBySort();
        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> buildTreeNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryVO createCategory(CategoryReq req) {
        Category saved = categoryRepository.save(Category.newCategory(
                req.getName(), req.getParentId(), req.getLevel(), req.getSortOrder()));
        return categoryConverter.toVO(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public CategoryVO updateCategory(Long id, CategoryReq req) {
        Category revised = Category.forRevise(id, req.getName(), req.getParentId(),
                req.getLevel(), req.getSortOrder());
        categoryRepository.update(revised);
        return categoryConverter.toVO(revised);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    private CategoryTreeVO buildTreeNode(Category category, Map<Long, List<Category>> childrenMap) {
        CategoryTreeVO vo = categoryConverter.toTreeVO(category);
        List<Category> children = childrenMap.getOrDefault(category.getId(), List.of());
        if (!children.isEmpty()) {
            vo.setChildren(children.stream()
                    .map(child -> buildTreeNode(child, childrenMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}
