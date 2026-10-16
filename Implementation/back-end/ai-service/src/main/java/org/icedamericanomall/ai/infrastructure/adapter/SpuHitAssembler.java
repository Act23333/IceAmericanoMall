package org.icedamericanomall.ai.infrastructure.adapter;

import org.icedamericanomall.ai.domain.model.spu.SpuHit;

import java.util.Map;

/**
 * 搜索结果 Map → {@link SpuHit} 装配器（基础设施层内部工具）。
 *
 * <p>search-service 的商品报文是字段名固定的 Map（{@code spuId/name/description/price/soldCount}），
 * 在此集中做类型归一（数字可能为 Integer/Long/String），避免各适配器重复解析。
 */
final class SpuHitAssembler {

    private SpuHitAssembler() {
    }

    /** 从搜索结果 Map 装配命中项。 */
    static SpuHit fromMap(Map<?, ?> raw) {
        return new SpuHit(
                asString(raw.get("spuId")),
                asString(raw.get("name")),
                asString(raw.get("description")),
                asInt(raw.get("price")),
                asInt(raw.get("soldCount")));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
