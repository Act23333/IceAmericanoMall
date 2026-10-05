package org.icedamericanomall.client;

import org.icedamericanomall.dto.NoteVectorHitCO;
import org.icedamericanomall.dto.NoteVectorUpsertCO;
import org.icedamericanomall.fallback.NoteVectorClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 笔记向量推荐 Feign 客户端（F12 内容社区：item-service → search-service，
 * 契约 §4.14.10 I50-I51，V5.13；ADR-046 管道第二次复用）。
 *
 * <p>调用方：item-service——笔记发布/软删后摄取向量（事务外旁路 afterCommit）、
 * P34 相似笔记组装。
 *
 * <p><b>降级语义：fail-open（纯旁路，ADR-046 同口径）</b>——摄取失败哨兵 {@code -1}
 * （对齐 I47 哨兵口径），相似检索失败空列表；笔记主流程零感知。
 */
@FeignClient(name = "search-service", path = "/internal/search/vector/note",
        contextId = "noteVector", fallbackFactory = NoteVectorClientFallback.class)
public interface NoteVectorClient {

    /**
     * 笔记向量幂等覆盖写（单向量，title+content 文本源）。
     *
     * @param co 摄取参数（noteId 幂等键；deleted=true 表示从索引移除）
     * @return 1=已写入/已删除；0=ES/Embedding 未启用（静默跳过）；降级哨兵=-1
     */
    @PostMapping("/upsert")
    int upsert(@RequestBody NoteVectorUpsertCO co);

    /**
     * 相似笔记检索（自身向量 kNN，排除自身）。
     *
     * @param noteId 笔记 id
     * @param size   返回条数（缺省 5，≤20）
     * @return 命中 id + 相似度得分（降序）；降级/无向量时为空列表
     */
    @GetMapping("/similar")
    List<NoteVectorHitCO> similar(@RequestParam("noteId") Long noteId,
                                  @RequestParam(value = "size", defaultValue = "5") int size);
}
