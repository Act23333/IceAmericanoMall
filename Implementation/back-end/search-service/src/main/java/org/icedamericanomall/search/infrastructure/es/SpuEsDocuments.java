package org.icedamericanomall.search.infrastructure.es;

import org.icedamericanomall.dto.SpuSearchItemCO;
import org.icedamericanomall.search.domain.model.search.SpuSearchHit;
import org.icedamericanomall.search.domain.model.search.SpuVectorDoc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品 ES 文档转换（纯函数，包内可见便于单测）—— 自 {@code EsSpuSearchGateway} 下沉：
 * F8 SE1 过滤/排序与 SE6 facet 加入后主类逼近 300 行红线（09-constraints 方法/类规模硬约束），
 * 故将「检索卡片 ↔ ES 文档 ↔ 领域命中」的纯转换职责独立成类，网关只保留检索编排。
 *
 * <p><b>数据面（ADR-052）</b>：入参由原 {@code SpuPO}（直读镜像表）改为 item-service
 * 内部检索端点的 {@link SpuSearchItemCO}——{@code spuId} 已是 hex String、
 * {@code publishTime} 已是 epoch millis（转换在 item 域持久化边界完成，ADR-050）。
 */
final class SpuEsDocuments {

    private SpuEsDocuments() {
    }

    /** 检索卡片 → ES doc map（embedding 为 null 时不落该字段，即无向量文档）。 */
    static Map<String, Object> toDocMap(SpuSearchItemCO p, float[] embedding) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", p.getId());
        doc.put("spuId", p.getSpuId());
        doc.put("name", p.getName());
        doc.put("description", p.getDescription());
        doc.put("brand", p.getBrand());
        doc.put("categoryId", p.getCategoryId());
        doc.put("price", p.getMinPrice() == null ? 0 : p.getMinPrice());
        doc.put("mainImage", p.getMainImage());
        doc.put("soldCount", p.getSoldCount());
        if (p.getPublishTime() != null) {
            doc.put("publishTime", p.getPublishTime());
        }
        if (embedding != null) {
            doc.put(SpuVectorMapping.VECTOR_FIELD, toFloatList(embedding));
        }
        return doc;
    }

    /**
     * 领域向量文档 → ES 文档。
     *
     * <p><b>标识分工（ADR-061 §决策 2 / ADR-046）</b>：{@code _id}/{@code id} = 商品
     * <b>技术键</b>（与 reindex / I28 检索卡片命中 id 同源，ES 侧仅此一种文档键），
     * {@code spuId} = 商品<b>业务键</b> 32-hex（I47 幂等键与 I48 命中的线格式标识）。
     * 技术键由网关先经 item 域检索出口解析（{@code /detail/key/{spuId}}）后传入。
     *
     * @param doc    领域向量文档（{@code spuId} 为业务键）
     * @param id     商品技术键（调用方保证非空；解析不到时调用方不写文档）
     */
    static SpuDocument toDocument(SpuVectorDoc doc, Long id) {
        SpuDocument document = new SpuDocument();
        document.setId(id);
        document.setSpuId(doc.spuId());
        document.setName(doc.name());
        document.setDescription(doc.description());
        document.setBrand(doc.brand());
        document.setCategoryId(doc.categoryId());
        document.setMainImage(doc.mainImage());
        document.setPrice(doc.price());
        document.setSoldCount(doc.soldCount());
        document.setEmbedding(doc.embedding());
        return document;
    }

    /** ES 命中 → 领域读模型（无得分：关键词检索）。 */
    static SpuSearchHit toHit(SpuDocument doc) {
        return toHit(doc, null);
    }

    /** ES 命中 → 领域读模型（带得分：kNN 向量检索）。 */
    static SpuSearchHit toHit(SpuDocument doc, Float score) {
        return new SpuSearchHit(doc.getId(), doc.getSpuId(), doc.getCategoryId(), doc.getName(),
                doc.getDescription(), doc.getBrand(), doc.getMainImage(), doc.getPrice(), doc.getSoldCount(),
                score == null ? null : score.doubleValue());
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
