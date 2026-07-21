package org.icedAmericanoMall.dto;

import java.util.List;

/** BGE-reranker 请求 — Java Record。 */
public record RerankerRequest(String query, List<String> documents, int topK) {}
