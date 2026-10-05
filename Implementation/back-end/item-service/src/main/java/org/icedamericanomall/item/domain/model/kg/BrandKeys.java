package org.icedamericanomall.item.domain.model.kg;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * 品牌值节点键工具（F15 Phase 1，PRD §16.4 O4 裁决：品牌为规范化字符串值节点，无物理表）。
 *
 * <p><b>brandKey 规范化规则（归并口径）</b>：{@code trim → 统一小写（Locale.ROOT）→
 * 连续空白折叠为单个空格}。同名品牌的不同书写形态（大小写 / 首尾空格 / 多空格）
 * 归并为同一品牌值节点；中文品牌名不受小写化影响。规则刻意保持<b>纯函数、可重放</b>——
 * 增量摄取（I57）与全量重建（AD64）用同一规则产出同一 key，保证投影幂等一致。
 *
 * <p><b>brand 节点 dstId 口径（汇报口径，Phase 1 单点约定）</b>：{@code kg_edges} 边文档的
 * {@code dstId} 契约为 Long（ia-api {@code KgEdgeItemCO}），而品牌值节点无物理表、无数字主键——
 * 取 <b>brandKey 的 CRC-32（无符号 32 位，取值恒为正）</b>作为稳定节点 id：
 * <ul>
 *   <li>确定性：同 key 恒得同 id，增量/重建/多实例间一致，满足 I57 先删后写幂等；</li>
 *   <li>无碰撞期望：CRC-32 碰撞需 key 恰为 2^32 分之一同余，品牌词表万级规模下期望碰撞
 *       ≈ 词表²/2³³（≈1e-5 量级以下），Phase 1 可接受；</li>
 *   <li>语义边界：search-service 侧对 brand dstId 仅做<b>等值过滤/邻居返回</b>，不做语义解释；
 *       反解品牌名不走 dstId（展示需求由消费方持有 brandKey 原文解决，Phase 1 无此消费场景）。</li>
 * </ul>
 * Phase 2 若按 O4 实体化 brand 表，仅需本类替换为「key → 表主键」映射，摄取链路零改动。
 */
public final class BrandKeys {

    private BrandKeys() {
    }

    /**
     * 规范化品牌字符串为 brandKey。
     *
     * @param brand 原始品牌字符串（可空）
     * @return 规范化 key；入参空白时返回 {@code null}（调用方据此跳过 BRAND_OF 边）
     */
    public static String brandKey(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        return brand.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * 品牌值节点的稳定数字 id（brandKey 的 CRC-32，恒为正 Long，口径见类注释）。
     *
     * @param brand 原始品牌字符串（可空）
     * @return 节点 id；入参空白时返回 {@code null}
     */
    public static Long nodeId(String brand) {
        String key = brandKey(brand);
        if (key == null) {
            return null;
        }
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
