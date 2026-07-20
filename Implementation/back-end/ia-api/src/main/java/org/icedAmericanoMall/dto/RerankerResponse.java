package org.icedAmericanoMall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * V3.0: BGE-reranker 响应 — 重排序后的文档列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankerResponse {

    /** 按相关性降序排列的文档索引 */
    private List<RerankedDocument> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankedDocument {
        /** 原始文档在输入列表中的索引 */
        private int index;
        /** 重排序得分 (0-1) */
        private double score;
        /** 文档内容 */
        private String text;
    }
}
