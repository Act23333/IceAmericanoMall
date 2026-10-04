package org.icedamericanomall.search.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Embedding API 配置 —— OpenAI 兼容协议的向量化服务（DashScope / OpenAI / 自建均可）。
 *
 * <p>存在意义：向量索引归 search-service（ES 域）所有，因此<b>向量化也在这里发生</b>
 * （摄取时对每个 chunk 调本接口）。ai-service 只提交原文，不感知模型与维度，
 * 从根上避免「两处 Embedding 模型/dims 口径漂移」。
 *
 * <p>未配置（{@code enabled=false} 或 {@code api-key} 为空）即为「未启用」：
 * 摄取端点直接返回 0 并记 WARN，<b>不报错</b> —— 保证 ES/Embedding 未就绪时知识库 CRUD 完全不受影响。
 *
 * <p>{@code dims} 必须与 embedding 模型输出维度一致（默认 text-embedding-3-small = 1536），
 * 也是 {@code knowledge_vectors} 索引 dense_vector 的维度来源（单一事实源）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "search.embedding")
public class EmbeddingProperties {

    /** 总开关；false 时视为未启用（摄取直接跳过）。 */
    private boolean enabled = false;

    /** Embedding 服务基地址，如 https://dashscope.aliyuncs.com/compatible-mode/v1。 */
    private String baseUrl = "";

    /** 向量化子路径。 */
    private String path = "/embeddings";

    /** 模型名。 */
    private String model = "text-embedding-3-small";

    /** API Key（占位符由环境变量注入，不落代码）。 */
    private String apiKey = "";

    /** 向量维度（dense_vector dims 的唯一来源）。 */
    private int dims = 1536;

    /** 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** 读超时。 */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** 是否已具备调用条件（enabled + baseUrl + apiKey 齐备）。 */
    public boolean isConfigured() {
        return enabled && !isBlank(baseUrl) && !isBlank(apiKey);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
