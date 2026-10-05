package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 知识图谱检索面降级工厂 —— <b>全链路 fail-open</b>（KG 纯旁路增强能力，PRD §16.8.1 最高优先级纪律；
 * 定类对齐 ADR-046「推荐纯旁路全链路 fail-open」与 I47 哨兵先例）。
 */
@Slf4j
@Component
public class KgClientFallback implements FallbackFactory<KgClient> {

    /** 摄取降级哨兵（对齐 I47 口径）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    @Override
    public KgClient create(Throwable cause) {
        return new KgClient() {

            @Override
            public int edgeUpsert(KgEdgeUpsertCO upsert) {
                log.warn("KG 边摄取降级：search-service 不可用，本批边未投影（WARN 不阻断主流程）"
                                + " srcType={}, srcId={}, edgeCount={}",
                        upsert == null ? null : upsert.getSrcType(),
                        upsert == null ? null : upsert.getSrcId(),
                        upsert == null || upsert.getEdges() == null ? 0 : upsert.getEdges().size(), cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<KgNeighborCO> neighbors(String srcType, Long srcId, String relType,
                                                String dstType, Integer depth, Integer status) {
                log.warn("KG 邻居查询降级：search-service 不可用，返回空列表（展示型查询返回空值，ADR-011）"
                                + " srcType={}, srcId={}", srcType, srcId, cause);
                return Collections.emptyList();
            }

            @Override
            public void deleteEdges(String srcType, Long srcId) {
                log.warn("KG 清边降级：search-service 不可用，本次清边未执行（重建兜底，ADR-049 无关）"
                        + " srcType={}, srcId={}", srcType, srcId, cause);
            }
        };
    }
}
