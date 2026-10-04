package org.icedamericanomall.ai.app;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.app.dto.AiChatRequest;
import org.icedamericanomall.ai.app.dto.AiChatResponse;
import org.icedamericanomall.ai.domain.service.ContentSafetyFilter;
import org.icedamericanomall.ai.infrastructure.agent.CustomerServiceAssistant;
import org.icedamericanomall.ai.infrastructure.agent.StreamingCustomerServiceAssistant;
import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.icedamericanomall.ai.infrastructure.config.RedisChatMemoryStore;
import org.icedamericanomall.ai.infrastructure.rag.RAGRetriever;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * 智能客服编排 Manager（应用层）—— 内容安全 → 知识库 RAG 上下文 → Agent 调用 → 置信度转人工。
 * <p>
 * 原先散落在 {@code CustomerServiceController} 的编排分支（启用判断、转人工判定、兜底文案）
 * 全部收拢到本类，Controller 只做参数校验与 Result 包装。
 * <p>
 * 转人工能力（置信度评估 + 转接文案生成）由原独立服务（顶层 {@code service/HandoffService}）
 * 并入本类：它只有一个调用方（客服对话链路），与对话编排同属一个用例族，
 * 按「一个用例族一个 Manager」收敛，不再保留顶层游离包。
 * <p>
 * 客服 Agent 仅在 {@code ai.enabled=true} 时存在，故以 {@code @Nullable} 注入，
 * 不用 AI 能力时服务仍可正常启动。
 *
 * <h2>知识库 RAG（V5.4，渐进启用 + 全链路降级）</h2>
 * 仅当 {@code ai.rag.enabled=true} <b>且</b>请求带 {@code sellerId} 时，先经
 * {@link RAGRetriever#retrieveForSeller} 取该商家知识 chunk 并拼进 prompt；
 * 检索失败或为空（返回 null）→ 原样使用用户问题，<b>无 RAG 直接应答</b>。
 * 开关关闭时不调用检索器（零触达），行为与接入前完全一致。
 */
@Slf4j
@Component
public class CsManager {

    /** 未登录用户的 memoryId 占位标识。 */
    private static final String ANONYMOUS_USER = "anonymous";

    /** AI 未启用时的兜底文案。 */
    private static final String UNAVAILABLE_MESSAGE =
            "AI 客服未启用。请设置 ai.enabled=true 并配置 DEEPSEEK_API_KEY。";

    /** RAG 上下文 prompt 模板前缀。 */
    private static final String KNOWLEDGE_PROMPT_PREFIX = "请参考以下商家知识回答用户问题：\n";

    /** RAG 上下文 prompt 模板分隔。 */
    private static final String KNOWLEDGE_PROMPT_SEPARATOR = "\n用户问题：";

    /** 转人工置信度阈值（低于即转人工）。 */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    /** 低置信度判定关键词（命中越多置信度越低）。 */
    private static final Set<String> UNCERTAIN_KEYWORDS =
            Set.of("不确定", "无法", "抱歉", "请联系", "我不", "暂无");

    private final AiProperties aiProperties;
    private final ContentSafetyFilter safetyFilter;
    private final ChatModel chatModel;
    private final CustomerServiceAssistant customerServiceAssistant;
    private final StreamingCustomerServiceAssistant streamingAssistant;
    private final RAGRetriever ragRetriever;

    public CsManager(AiProperties aiProperties,
                     ContentSafetyFilter safetyFilter,
                     ChatModel chatModel,
                     @Nullable CustomerServiceAssistant customerServiceAssistant,
                     @Nullable StreamingCustomerServiceAssistant streamingAssistant,
                     @Nullable RAGRetriever ragRetriever) {
        this.aiProperties = aiProperties;
        this.safetyFilter = safetyFilter;
        this.chatModel = chatModel;
        this.customerServiceAssistant = customerServiceAssistant;
        this.streamingAssistant = streamingAssistant;
        this.ragRetriever = ragRetriever;
    }

    /**
     * 同步客服对话：输入安全校验 → 知识库 RAG 上下文 → Agent 调用 → 低置信度自动转人工。
     *
     * @param req    对话请求
     * @param userId 当前用户ID（未登录为 null）
     * @return 回复与规范化后的会话ID
     * @throws BizException 输入未通过内容安全审核（AI_CONTENT_FILTERED）
     */
    public AiChatResponse chat(AiChatRequest req, Long userId) {
        String rejected = safetyFilter.checkInput(req.message());
        if (rejected != null) {
            log.warn("内容安全拦截: {}", rejected);
            throw new BizException(ErrorCode.AI_CONTENT_FILTERED, rejected);
        }
        String conversationId = resolveConversationId(req.conversationId());
        if (!aiProperties.isEnabled() || customerServiceAssistant == null) {
            return new AiChatResponse(UNAVAILABLE_MESSAGE, conversationId);
        }
        String memoryId = memoryId(userId, conversationId);
        String reply = customerServiceAssistant.chat(memoryId, enrichWithKnowledge(req));
        if (needsHandoff(reply)) {
            log.info("Handoff triggered: memoryId={}", memoryId);
            reply = generateHandoffMessage(req.message(), reply);
        }
        return new AiChatResponse(reply, conversationId);
    }

    /**
     * 流式客服对话：AI 未启用或流式 Agent 缺失时返回 null，由适配层输出兜底文案。
     *
     * @param req    对话请求
     * @param userId 当前用户ID（未登录为 null）
     * @return TokenStream；不可用时为 null
     */
    @Nullable
    public TokenStream stream(AiChatRequest req, Long userId) {
        if (!aiProperties.isEnabled() || streamingAssistant == null) {
            return null;
        }
        return streamingAssistant.chat(memoryId(userId, resolveConversationId(req.conversationId())),
                enrichWithKnowledge(req));
    }

    /**
     * 用商家知识库上下文增强用户问题（RAG prompt 拼装）。
     *
     * <p>任一前置条件不满足即返回原问题（零副作用）：RAG 关闭、未带 sellerId、检索器缺失；
     * 检索异常或无命中同样降级为原问题 —— <b>绝不因检索问题打断客服应答</b>。
     *
     * @param req 对话请求
     * @return 拼装了知识上下文的 prompt；无可用上下文时返回 {@code req.message()}
     */
    private String enrichWithKnowledge(AiChatRequest req) {
        if (!aiProperties.getRag().isEnabled() || req.sellerId() == null || ragRetriever == null) {
            return req.message();
        }
        try {
            String context = ragRetriever.retrieveForSeller(req.message(), req.sellerId());
            if (context == null || context.isBlank()) {
                return req.message();
            }
            return KNOWLEDGE_PROMPT_PREFIX + context + KNOWLEDGE_PROMPT_SEPARATOR + req.message();
        } catch (Exception e) {
            log.warn("RAG 上下文拼装失败（降级为无 RAG 应答）: sellerId={}, cause={}",
                    req.sellerId(), e.getMessage());
            return req.message();
        }
    }

    /**
     * 评估 AI 回答的置信度。
     * 简单启发式：回答包含"不确定/无法/抱歉/请联系"等词 → 低置信度。
     */
    private double evaluateConfidence(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) return 0.0;
        String lower = aiReply.toLowerCase();
        int uncertainCount = 0;
        for (String keyword : UNCERTAIN_KEYWORDS) {
            if (lower.contains(keyword)) uncertainCount++;
        }
        if (uncertainCount == 0) return 0.95;
        if (uncertainCount == 1) return 0.7;
        return 0.5;
    }

    /**
     * 判断是否需要转人工。
     */
    private boolean needsHandoff(String aiReply) {
        return evaluateConfidence(aiReply) < CONFIDENCE_THRESHOLD;
    }

    /**
     * 生成对话摘要 + 转接通知。
     */
    private String generateHandoffMessage(String userMessage, String aiReply) {
        double confidence = evaluateConfidence(aiReply);
        String summary = chatModel.chat(String.format(
                "请用一句话总结以下用户问题（不超过30字）：%s", userMessage));
        return String.format(
                "【转人工】置信度: %.0f%% | 用户问题: %s | AI初步回复: %s | 对话摘要: %s | 已为您转接人工客服，请稍候。",
                confidence * 100, userMessage, aiReply, summary);
    }

    /** 组装会话 memoryId：{@code cs:{userId}:{conversationId}}。 */
    public String memoryId(Long userId, String conversationId) {
        String userKey = userId == null ? ANONYMOUS_USER : String.valueOf(userId);
        return RedisChatMemoryStore.memoryId(RedisChatMemoryStore.CS_AGENT, userKey, conversationId);
    }

    /** 会话ID 为空时生成 UUID。 */
    public String resolveConversationId(String requested) {
        return requested != null && !requested.isBlank() ? requested : UUID.randomUUID().toString();
    }

    /** AI 未启用时的兜底文案（适配层流式分支复用，避免文案重复）。 */
    public String unavailableMessage() {
        return UNAVAILABLE_MESSAGE;
    }
}
