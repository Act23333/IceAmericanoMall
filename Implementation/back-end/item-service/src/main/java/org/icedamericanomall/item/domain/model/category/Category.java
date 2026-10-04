package org.icedamericanomall.item.domain.model.category;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类聚合根（纯 POJO，零框架依赖）。
 *
 * <p><b>聚合边界</b>：分类是独立聚合；{@code Product} 通过 {@code categoryId} 按 ID 引用，
 * 不持有对象引用（跨聚合一致性由应用层编排 + 唯一索引兜底）。
 *
 * <p>分类树装配（{@code parentId} 递归）属<b>读模型装配</b>，落在应用层
 * {@code CategoryManager}，不污染聚合。
 */
@Data
@Builder
public class Category {

    private Long id;

    private String name;

    /** 父分类 ID；根节点为 {@code null}。 */
    private Long parentId;

    private Integer level;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /** 新建分类（未落库，{@code id} 由仓储补齐）。 */
    public static Category newCategory(String name, Long parentId, Integer level, Integer sortOrder) {
        return Category.builder()
                .name(name)
                .parentId(parentId)
                .level(level)
                .sortOrder(sortOrder)
                .build();
    }

    /**
     * 构造"编辑态"分类（仅用于落库更新，不读库）。
     *
     * <p>口径与历史实现一致：编辑为<b>直接覆盖</b>四个可写字段、<b>不校验存在性</b>
     * （不存在的 ID 更新影响 0 行、静默返回），故此处不做"先装载后编辑"的读改写。
     */
    public static Category forRevise(Long id, String name, Long parentId, Integer level, Integer sortOrder) {
        return Category.builder()
                .id(id)
                .name(name)
                .parentId(parentId)
                .level(level)
                .sortOrder(sortOrder)
                .build();
    }
}
