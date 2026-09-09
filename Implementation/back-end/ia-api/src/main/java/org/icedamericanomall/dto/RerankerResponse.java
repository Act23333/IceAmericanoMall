package org.icedamericanomall.dto;

import java.util.List;

/** BGE-reranker 响应 — Java Record。 */
public record RerankerResponse(List<RerankedDocument> results) {
    public record RerankedDocument(int index, double score, String text) {}
}
