package org.icedAmericanoMall.evaluation;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V3.0.3: LLM-as-Judge 离线评估器。
 * <p>
 * 自动评分指标：
 * <ul>
 *   <li>Faithfulness (忠实度): 回答是否被检索文档支持, 目标 > 90%</li>
 *   <li>Answer Relevance (答案相关性): 回答是否切题, 目标 > 85%</li>
 *   <li>Context Recall (上下文召回): 检索是否覆盖答案所需信息, 目标 > 85%</li>
 * </ul>
 * <p>
 * 大厂对标: Shopify Sidekick LLM-as-Judge + Cohen's Kappa 统计校验
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class LLMJudge {

    private final ChatModel chatModel;

    public LLMJudge(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 评估一次 RAG 问答的质量。
     *
     * @param question 用户问题
     * @param answer   AI 生成的回答
     * @param context  检索到的上下文
     * @return 评估结果 (faithfulness/relevance/recall 0-100)
     */
    public EvaluationResult evaluate(String question, String answer, String context) {
        int faithfulness = scoreFaithfulness(question, answer, context);
        int relevance = scoreRelevance(question, answer);
        int recall = scoreContextRecall(question, context);
        return new EvaluationResult(faithfulness, relevance, recall);
    }

    private int scoreFaithfulness(String question, String answer, String context) {
        String prompt = String.format("""
                评估以下AI回答的忠实度——回答中的每句话是否都可以在被检索的上下文中找到依据。
                请只回复0到100之间的分数。

                问题: %s
                被检索的上下文: %s
                AI回答: %s

                忠实度分数:""", question, context, answer);
        return parseScore(chatModel.chat(prompt));
    }

    private int scoreRelevance(String question, String answer) {
        String prompt = String.format("""
                评估以下AI回答与用户问题的相关性。回答是否直接回应了问题？
                请只回复0到100之间的分数。

                问题: %s
                AI回答: %s

                相关性分数:""", question, answer);
        return parseScore(chatModel.chat(prompt));
    }

    private int scoreContextRecall(String question, String context) {
        String prompt = String.format("""
                评估被检索到的上下文对被回答此问题所需信息的覆盖程度。
                请只回复0到100之间的分数。

                问题: %s
                被检索到的上下文: %s

                上下文召回分数:""", question, context);
        return parseScore(chatModel.chat(prompt));
    }

    private int parseScore(String response) {
        try {
            String cleaned = response.replaceAll("[^0-9]", " ").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length > 0) {
                return Math.clamp(Integer.parseInt(parts[0]), 0, 100);
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse LLM score: {}", response);
        }
        return 50; // 默认中等分数
    }

    /**
     * 评估结果。
     */
    public record EvaluationResult(int faithfulness, int relevance, int contextRecall) {

        public boolean isPassing() {
            return faithfulness >= 90 && relevance >= 85 && contextRecall >= 85;
        }

        public Map<String, Integer> toMap() {
            return Map.of("faithfulness", faithfulness,
                    "relevance", relevance,
                    "contextRecall", contextRecall);
        }
    }
}
