package org.icedAmericanoMall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * V3.0: BGE-reranker 请求 — 对检索结果重排序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankerRequest {

    /** 用户原始查询 */
    private String query;
    /** 待排序文档列表 */
    private List<String> documents;
    /** 返回 Top-K 数量 */
    private int topK;
}
