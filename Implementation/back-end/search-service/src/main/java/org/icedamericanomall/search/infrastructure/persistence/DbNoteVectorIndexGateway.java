package org.icedamericanomall.search.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.search.domain.gateway.NoteVectorIndexGateway;
import org.icedamericanomall.search.domain.model.search.NoteVectorDoc;
import org.icedamericanomall.search.domain.model.search.NoteVectorHit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 笔记向量索引 DB 实现 —— ES 未启用时的缺省实现（向量是 ES 能力，全部为空实现）。
 *
 * <p>与 {@code DbSpuSearchGateway} 的向量方法同口径：{@code upsertVectorDoc}/{@code deleteById}
 * 为空操作、{@code findVectorById} 恒 empty、{@code similar} 恒返回空列表 ——
 * I50 摄取按「未启用」跳过（调用方零感知），I51 相似检索按无命中返回（推荐位可空）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DbNoteVectorIndexGateway implements NoteVectorIndexGateway {

    @Override
    public void upsertVectorDoc(NoteVectorDoc doc) {
        // DB 链无向量能力，摄取为空操作（向量是 ES 能力，仅 ES 链路实现）
    }

    @Override
    public Optional<double[]> findVectorById(Long noteId) {
        // DB 链无向量能力，恒视为无向量（fail-open）
        return Optional.empty();
    }

    @Override
    public void deleteById(Long noteId) {
        // DB 链无向量能力，删除为空操作
    }

    @Override
    public List<NoteVectorHit> similar(double[] embedding, int size) {
        // DB 不支持向量搜索，返回空列表（降级处理）
        return Collections.emptyList();
    }
}
