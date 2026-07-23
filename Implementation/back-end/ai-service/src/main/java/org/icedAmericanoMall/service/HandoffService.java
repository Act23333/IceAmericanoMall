package org.icedAmericanoMall.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * V3.0: 人工转接服务 — 置信度评估 + 对话摘要生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final ChatModel chatModel;

    private static final double CONFIDENCE_THRESHOLD = 0.7;

    /**
     * 评估 AI 回答的置信度。
     * 简单启发式：回答包含"不确定/无法/抱歉/请联系"等词 → 低置信度。
     */
    public double evaluateConfidence(String aiReply) {
        if (aiReply == null || aiReply.isBlank()) return 0.0;
        String lower = aiReply.toLowerCase();
        int uncertainCount = 0;
        for (String keyword : new String[]{"不确定", "无法", "抱歉", "请联系", "我不", "暂无"}) {
            if (lower.contains(keyword)) uncertainCount++;
        }
        if (uncertainCount == 0) return 0.95;
        if (uncertainCount == 1) return 0.7;
        return 0.5;
    }

    /**
     * 判断是否需要转人工。
     */
    public boolean needsHandoff(String aiReply) {
        return evaluateConfidence(aiReply) < CONFIDENCE_THRESHOLD;
    }

    /**
     * 生成对话摘要 + 转接通知。
     */
    public String generateHandoffMessage(String userMessage, String aiReply) {
        double confidence = evaluateConfidence(aiReply);
        String summary = chatModel.chat(String.format(
                "请用一句话总结以下用户问题（不超过30字）：%s", userMessage));
        return String.format(
                "【转人工】置信度: %.0f%% | 用户问题: %s | AI初步回复: %s | 对话摘要: %s | 已为您转接人工客服，请稍候。",
                confidence * 100, userMessage, aiReply, summary);
    }
}
