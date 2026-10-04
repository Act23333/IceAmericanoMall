package org.icedamericanomall.notification.domain.model;

import java.util.List;

/**
 * 领域分页结果（纯 JDK 值对象，无任何框架类型）。
 *
 * <p>承载领域仓储端口的分页返回，避免 {@code domain} 包直接依赖 MyBatis-Plus 的
 * {@code IPage}；基础设施层实现负责与 {@code IPage} 互转，应用层/适配层再按其
 * 需要组装出站报文。
 *
 * @param records  当前页记录（null 归一化为空列表）
 * @param total    总记录数
 * @param pageNum  当前页码（从 1 开始）
 * @param pageSize 每页条数
 * @param <T>      记录类型
 */
public record PageResult<T>(List<T> records, long total, long pageNum, long pageSize) {

    /** 紧凑构造器：records 为空时归一化为不可变空列表，避免调用方空指针。 */
    public PageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
