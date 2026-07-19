package org.icedAmericanoMall.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.rag.RAGRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * V2.5.2: LangChain4j @Tool — RAG 知识库检索。
 * <p>
 * 向量语义检索商品/FAQ 知识，供客服 Agent 在回答政策类/商品类问题时调用。
 * 检索流程：Query → Embedding → ES kNN → 上下文组装 → 返回给 LLM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGSearchTool {

    private final RAGRetriever retriever;

    @Tool("检索平台知识库：根据用户问题搜索相关商品信息、退换货政策、FAQ等，返回最匹配的内容摘要")
    public String ragSearch(String query) {
        log.debug("RAGSearchTool called with query: {}", query);
        String context = retriever.retrieve(query);
        if (context.contains("暂无相关上下文")) {
            return "知识库中暂未找到与「" + query + "」直接相关的信息。请根据您的知识回答，如果不确定请建议用户联系人工客服。";
        }
        return context;
    }
}
