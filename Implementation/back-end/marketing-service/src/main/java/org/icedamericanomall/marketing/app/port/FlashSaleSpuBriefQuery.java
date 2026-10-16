package org.icedamericanomall.marketing.app.port;

import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;

import java.util.Collection;
import java.util.Map;

/**
 * 秒杀商品展示摘要查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>存在理由</b>：{@code flash_sale} 只存商品/SKU ID，契约 §4.10.2 {@code FlashSaleVO}
 * 要求投影 {@code product_name} / {@code product_image} / {@code original_price}
 * （05-API-Specification 为唯一真相源）；这些字段不属于秒杀聚合，故以独立读模型端口获取。
 *
 * <p><b>为何按 SKU ID 批量</b>：秒杀列表一页 N 行只打一次数据源，禁止逐行查询（N+1）。
 *
 * <p><b>边界说明（架构债，已在交付报告中登记）</b>：当前基础设施实现为同库读模型 JOIN
 * （marketing 与 item 共用同一 schema）。与 ADR-052「跨域数据经 {@code /internal/**} 出口」
 * 的方向存在张力——隔离点即本端口：后续如新增 item-service 批量商品摘要内部端点，
 * 只需替换实现类，应用层与适配层零改动。另：正因为是<b>本地读</b>，
 * {@code FlashSaleAdminManager}（{@code @Transactional}）才可安全调用
 * ——若改 Feign，则事务内调用将违反 09-constraints「事务内禁止 Feign」。
 */
public interface FlashSaleSpuBriefQuery {

    /**
     * 按 SKU ID 批量查询商品展示摘要。
     *
     * @param skuIds SKU ID 集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return SKU ID → 摘要；SKU 或商品不存在时<b>不出现</b>在返回 Map 中
     */
    Map<Long, FlashSaleSpuBrief> findBySkuIds(Collection<Long> skuIds);
}
