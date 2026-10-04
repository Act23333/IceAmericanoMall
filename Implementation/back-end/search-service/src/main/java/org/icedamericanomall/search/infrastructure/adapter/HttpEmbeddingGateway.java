package org.icedamericanomall.search.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.search.domain.gateway.EmbeddingGateway;
import org.icedamericanomall.search.infrastructure.config.EmbeddingProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embedding API 客户端（OpenAI 兼容协议）—— 把 chunk 文本向量化。
 *
 * <p>归属：向量索引归 search-service，向量化随之落在本服务；ai-service 只在检索时
 * 自带查询向量（其 EmbeddingService 与本客户端必须使用<b>同一模型/维度</b>，配置注释已标注）。
 *
 * <p>失败语义：<b>fail-loud（抛业务异常）</b>。摄取是显式写操作，向量化失败必须让调用方知道
 * 「本次未建立索引」，由 ai-service 侧记录 WARN 并降级（不影响知识库 CRUD）。
 * 未配置（{@link #isConfigured()} 为 false）时调用方直接跳过，<b>不调用本类</b>，故不报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpEmbeddingGateway implements EmbeddingGateway {

    private static final ParameterizedTypeReference<EmbeddingResponse> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final EmbeddingProperties properties;

    /** 延迟构建（首次调用时配置已绑定完成）。 */
    private volatile RestClient restClient;

    /** 是否已具备调用条件（enabled + baseUrl + apiKey 齐备）。 */
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * 文本向量化。
     *
     * @param text 输入文本（chunk 正文）
     * @return 定长向量（长度 = {@link EmbeddingProperties#getDims()}）
     * @throws BizException 调用失败、响应为空或维度与配置不一致（fail-loud）
     */
    public float[] embed(String text) {
        Map<String, Object> body = Map.of("model", properties.getModel(), "input", text);
        EmbeddingResponse response;
        try {
            response = client().post()
                    .uri(properties.getPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "Embedding 调用失败: " + e.getMessage());
        }
        List<Double> embedding = firstEmbedding(response);
        if (embedding == null || embedding.isEmpty()) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED, "Embedding 返回空向量");
        }
        if (properties.getDims() > 0 && embedding.size() != properties.getDims()) {
            throw new BizException(ErrorCode.THIRD_PARTY_CONNECT_FAILED,
                    "Embedding 维度不匹配: 期望 " + properties.getDims() + "，实际 " + embedding.size());
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }

    private static List<Double> firstEmbedding(EmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            return null;
        }
        return response.data().get(0).embedding();
    }

    private RestClient client() {
        RestClient client = restClient;
        if (client == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
            factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
            client = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .requestFactory(factory)
                    .build();
            restClient = client;
        }
        return client;
    }

    /** OpenAI 兼容 Embedding 响应：{@code {"data":[{"embedding":[...]}]}}。 */
    public record EmbeddingResponse(List<EmbeddingData> data) {
    }

    /** 单条 embedding。 */
    public record EmbeddingData(List<Double> embedding) {
    }
}
