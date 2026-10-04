package org.icedamericanomall.item.domain.model.common;

import java.util.List;

/**
 * 分页读模型载体（领域层，框架无关）。
 *
 * <p><b>存在理由</b>：仓储端口必须能返回"一页领域对象 + 总数"，但领域层禁止依赖
 * MyBatis-Plus（{@code com.baomidou}），故不能直接透出 {@code IPage}。本记录承担
 * 仓储端口 ⇄ 应用层 ⇄ 适配层之间的分页信封：
 * <ul>
 *   <li>基础设施：{@code IPage} → 本对象（转换在 {@code XxxRepositoryImpl} 内）</li>
 *   <li>应用层：本对象 → 本对象（换元素类型为 {@code app/dto}）</li>
 *   <li>适配层：本对象 → {@code IPage<XxxVO>}（对外 JSON 形状不变）</li>
 * </ul>
 *
 * @param <T>       记录元素类型
 * @param records   当前页记录
 * @param total     总记录数
 * @param current   当前页码（从 1 起）
 * @param size      每页条数
 */
public record PageResult<T>(
        List<T> records,
        long total,
        long current,
        long size
) {

    /** 空页（total=0，页码/页大小仍按入参回填，保持信封字段完整）。 */
    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(List.of(), 0L, current, size);
    }

    /** 换元素类型（应用层 DTO 装配用），信封字段原样保留。 */
    public <R> PageResult<R> map(java.util.function.Function<T, R> mapper) {
        return new PageResult<>(records.stream().map(mapper).toList(), total, current, size);
    }
}
