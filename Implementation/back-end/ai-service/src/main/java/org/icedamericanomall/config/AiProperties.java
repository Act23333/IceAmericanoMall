package org.icedamericanomall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    @Data
    public static class Rag {
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
