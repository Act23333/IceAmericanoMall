package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 商品检索分页 DTO —— item-service 内部检索端点返回（Feign，ADR-052）。
 *
 * <p>页码/页长由调用方持有（search-service 侧构造领域 {@code SearchPage}），
 * 本 DTO 只承载结果集与总数，避免双端页码口径分叉。
 */
@Data
public class SpuSearchPageCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 命中总数 */
    private long total;

    /** 当前页命中条目 */
    private List<SpuSearchItemCO> records;

    /** 空页（降级/无命中统一口径）。 */
    public static SpuSearchPageCO empty() {
        SpuSearchPageCO page = new SpuSearchPageCO();
        page.setTotal(0L);
        page.setRecords(List.of());
        return page;
    }
}
