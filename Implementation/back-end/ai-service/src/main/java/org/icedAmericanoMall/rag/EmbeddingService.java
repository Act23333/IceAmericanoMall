package org.icedAmericanoMall.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * V2.5.2: Embedding 服务 — 文本向量化。
 * <p>
 * 通过 REST 调用 DeepSeek Embedding API（OpenAI 兼容协议），
 * 使用 text-embedding-3-small 模型（1536维）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class EmbeddingService {

    private final RestClient restClient;

    public EmbeddingService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Value("${spring.ai.openai.api-key:${DEEPSEEK_API_KEY:sk-placeholder}}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    private static final String EMBEDDING_MODEL = "text-embedding-3-small";

    /**
     * 将文本转换为向量。
     *
     * @param text 输入文本（查询或文档）
     * @return 1536维向量
     */
    @SuppressWarnings("unchecked")
    public double[] embed(String text) {
        try {
            Map<String, Object> body = Map.of(
                    "model", EMBEDDING_MODEL,
                    "input", text
            );

            Map<String, Object> respBody = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (respBody == null || !respBody.containsKey("data")) {
                log.warn("Embedding returned empty response for text: {}", text);
                return new double[0];
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) respBody.get("data");
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            return embedding.stream().mapToDouble(Double::doubleValue).toArray();
        } catch (Exception e) {
            log.warn("Embedding failed: {}", e.getMessage());
            return new double[0];
        }
    }
}
