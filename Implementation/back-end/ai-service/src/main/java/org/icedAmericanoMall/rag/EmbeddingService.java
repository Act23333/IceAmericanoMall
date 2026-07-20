package org.icedAmericanoMall.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
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

    private final RestTemplate restTemplate = new RestTemplate();

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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", EMBEDDING_MODEL,
                    "input", text
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/embeddings",
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<String, Object> respBody = response.getBody();
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
