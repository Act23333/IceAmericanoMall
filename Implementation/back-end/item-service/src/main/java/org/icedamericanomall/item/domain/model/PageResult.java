package org.icedamericanomall.item.domain.model;

import java.util.List;

/**
 * 领域分页结果（框架无关，ADR-052/架构守卫）—— 域层分页契约的**偿债目标形态**。
 *
 * <p>背景：域端口曾普遍以 MyBatis-Plus {@code IPage} 作返回类型（22 处，冻结于
 * {@code architecture-tests} 基线，只减不增）。新增域端口方法一律使用本类型，
 * 既有方法在后续迭代中逐步迁移；基础设施层在 RepositoryImpl 内完成 {@code IPage} → 本类型转换。
 *
 * @param records 当前页记录
 * @param total   总记录数
 * @param page    页码（1 起）
 * @param size    每页条数
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {

    /** 空页。 */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }
}
