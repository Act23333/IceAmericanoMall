package org.icedamericanomall.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 配置属性 — 绑定 application.yml ai.* 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 全局 AI 开关 — 关闭时所有 Agent bean 不创建，使用降级响应 */
    private boolean enabled = false;

    /** V3.0: 模型配置 */
    private Models models = new Models();

    /** V3.0: 路由配置 */
    private Routing routing = new Routing();

    /** V3.0: RAG 配置 */
    private Rag rag = new Rag();

    /** V2.5: ChatMemory TTL */
    private Memory memory = new Memory();

    /** 出站 HTTP 客户端超时（Embedding / Reranker 等自建 RestClient 共用） */
    private Http http = new Http();

    /** 买家-商家 IM 的 STOMP 配置 */
    private Websocket websocket = new Websocket();

    /**
     * 出站 HTTP 超时配置 —— 供 infra 层的 RestClient Bean 使用（servlet 环境无 RestClient Bean，
     * 需在 ai-service 自建，见 {@code infrastructure.config.AiRestClientConfig}）。
     */
    @Data
    public static class Http {
        /** 连接超时 */
        private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(2);
        /** 读超时（Embedding/重排序为同步阻塞调用，需留足余量） */
        private java.time.Duration readTimeout = java.time.Duration.ofSeconds(10);
    }

    @Data
    public static class Websocket {
        /** STOMP 握手来源白名单（默认仅本地开发地址） */
        private List<String> allowedOrigins = List.of(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000");
    }

    @Data
    public static class Models {
        private ModelConfig deepseek = new ModelConfig();
        private ModelConfig qwen = new ModelConfig();
    }

    @Data
    public static class ModelConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private int weight = 50;
    }

    @Data
    public static class Routing {
        /** 复杂度阈值 — >=此值的query路由到Qwen */
        private int complexityThreshold = 3;
        /** 每用户每日Token配额 */
        private int dailyTokenQuota = 50000;
    }

    /**
     * RAG 配置。
     *
     * <p>{@link #enabled} 是知识库 RAG 的<b>总开关，默认 false</b> —— 关闭时行为与「RAG 未接入」
     * 完全一致：知识库 CRUD 不触达向量索引、客服应答不拼接检索上下文。ES 与 Embedding API 就绪后
     * 才打开，属渐进启用。
     */
    @Data
    public static class Rag {
        /** 知识库 RAG 开关（默认关闭 = 现状不变） */
        private boolean enabled = false;
        /** 检索送入 LLM 的上下文条数（Top-K） */
        private int topK = 5;
        private Reranker reranker = new Reranker();
    }

    @Data
    public static class Reranker {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8001";
    }

    @Data
    public static class Memory {
        private java.time.Duration ttl = java.time.Duration.ofHours(24);
    }
}
