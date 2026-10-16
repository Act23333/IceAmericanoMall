package org.icedamericanomall.trade.domain.model.order;

import java.util.List;

/**
 * 领域分页结果（框架无关，ADR-038 / architecture-tests 分层守卫）。
 *
 * <p><b>为何需要它</b>：域层端口以 MyBatis-Plus {@code IPage} 作返回类型属架构违规（域层依赖框架），
 * 存量违规冻结在 {@code architecture-tests/archunit_store} 基线且<b>只减不增</b>。
 * 新增或改签名的域端口方法一律使用本类型；基础设施实现在内部完成
 * {@code IPage} → 本类型的转换，适配层需要的框架分页信封由应用层转换。
 *
 * @param records 当前页记录
 * @param total   总记录数（不分页口径）
 * @param page    页码（1 起）
 * @param size    每页条数
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {

    /** 空页。 */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }
}
