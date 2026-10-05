package org.icedamericanomall.search.infrastructure.es;

import org.icedamericanomall.search.domain.model.search.ProductSearchHit;
import org.icedamericanomall.search.domain.model.search.ProductVectorDoc;
import org.icedamericanomall.search.infrastructure.persistence.ProductPO;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品 ES 文档转换（纯函数，包内可见便于单测）—— 自 {@code EsProductSearchGateway} 下沉：
 * F8 SE1 过滤/排序与 SE6 facet 加入后主类逼近 300 行红线（09-constraints 方法/类规模硬约束），
 * 故将「PO ↔ ES 文档 ↔ 领域命中」的纯转换职责独立成类，网关只保留检索编排。
 */
final class ProductEsDocuments {

    private ProductEsDocuments() {
    }

    /** 商品 PO → ES doc map（embedding 为 null 时不落该字段，即无向量文档）。 */
    static Map<String, Object> toDocMap(ProductPO p, Map<Long, Integer> priceMap, float[] embedding) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", p.getId());
        doc.put("productId", p.getProductId());
        doc.put("name", p.getName());
        doc.put("description", p.getDescription());
        doc.put("brand", p.getBrand());
        doc.put("categoryId", p.getCategoryId());
        doc.put("price", priceMap.getOrDefault(p.getId(), 0));
        doc.put("mainImage", p.getMainImage());
        doc.put("soldCount", p.getSoldCount());
        if (p.getPublishTime() != null) {
            doc.put("publishTime", toEpochMilli(p.getPublishTime()));
        }
        if (embedding != null) {
            doc.put(ProductVectorMapping.VECTOR_FIELD, toFloatList(embedding));
        }
        return doc;
    }

    /** 领域向量文档 → ES 文档（_id = 商品 id，幂等覆盖；展示字段一并以入参为准）。 */
    static ProductDocument toDocument(ProductVectorDoc doc) {
        ProductDocument document = new ProductDocument();
        document.setId(doc.productId());
        document.setProductId(String.valueOf(doc.productId()));
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
    static ProductSearchHit toHit(ProductDocument doc) {
        return toHit(doc, null);
    }

    /** ES 命中 → 领域读模型（带得分：kNN 向量检索）。 */
    static ProductSearchHit toHit(ProductDocument doc, Float score) {
        return new ProductSearchHit(doc.getId(), doc.getProductId(), doc.getCategoryId(), doc.getName(),
                doc.getDescription(), doc.getBrand(), doc.getMainImage(), doc.getPrice(), doc.getSoldCount(),
                score == null ? null : score.doubleValue());
    }

    /**
     * 上架时间 → epoch millis（SE1 newest 排序）。
     *
     * <p>排序只依赖相对次序：写入侧统一用系统时区换算（与 item-service 落库的本地时间口径一致），
     * ES {@code date} 类型把 epoch millis 当 UTC 解释，整体平移不影响排序结果。
     */
    static Long toEpochMilli(LocalDateTime publishTime) {
        return publishTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
