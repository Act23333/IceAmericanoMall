package org.icedAmericanoMall.routing;

import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * V3.0.1: 多模型路由决策层。
 * <p>
 * 混合路由策略：
 * <ul>
 *   <li>语义路由：LLM 分类器评估 query 复杂度 (1-5)，复杂度>=阈值 → Qwen</li>
 *   <li>成本路由：每用户每日 Token 配额 (50K/天)，超限降级到更便宜模型</li>
 *   <li>故障转移：3 次指数退避重试后切换 provider</li>
 * </ul>
 * <p>
 * 大厂对标: Amazon Rufus 多模型路由 (简单query→小模型, 复杂query→大模型)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class ModelRouter {

    private final OpenAiChatModel deepseekModel;
    private final OpenAiChatModel qwenModel;
    private final AiProperties aiProperties;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUOTA_KEY_PREFIX = "ai:quota:";
    private static final String CACHE_KEY_PREFIX = "ai:cache:";
    private static final String FAULT_KEY_PREFIX = "ai:fault:";

    public ModelRouter(OpenAiChatModel langchain4jChatModel,
                       AiProperties aiProperties,
                       RedisTemplate<String, String> redisTemplate) {
        this.deepseekModel = langchain4jChatModel; // 默认 DeepSeek Bean
        this.qwenModel = createQwenModel(aiProperties);
        this.aiProperties = aiProperties;
        this.redisTemplate = redisTemplate;
    }

    private OpenAiChatModel createQwenModel(AiProperties props) {
        var qwen = props.getModels().getQwen();
        return OpenAiChatModel.builder()
                .apiKey(qwen.getApiKey())
                .baseUrl(qwen.getBaseUrl())
                .modelName(qwen.getModel())
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 路由决策：根据用户 query 和配额选择最优模型。
     *
     * @param userId 用户ID
     * @param query  用户消息
     * @return 选中的 ChatModel
     */
    public OpenAiChatModel route(Long userId, String query) {
        // 1. 检查今日配额
        if (isQuotaExceeded(userId)) {
            log.info("Token quota exceeded for userId={}, routing to DeepSeek", userId);
            return deepseekModel;
        }

        // 2. 检查 DeepSeek 故障状态
        if (isFaulty("deepseek")) {
            log.info("DeepSeek is faulty, routing to Qwen");
            return qwenModel;
        }

        // 3. 语义路由：简单query走DeepSeek, 复杂query走Qwen (基于启发式复杂度)
        int complexity = estimateComplexity(query);
        int threshold = aiProperties.getRouting().getComplexityThreshold();
        if (complexity >= threshold) {
            log.debug("Complex query (complexity={}), routing to Qwen", complexity);
            return qwenModel;
        }

        // 4. 默认走 DeepSeek（成本优先 80%）
        return deepseekModel;
    }

    /**
     * 语义缓存：FAQ 常见问题 MD5 缓存。
     *
     * @return 缓存的响应，null 表示缓存未命中
     */
    public String getCachedResponse(String query) {
        try {
            String key = CACHE_KEY_PREFIX + md5(query);
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    public void cacheResponse(String query, String response) {
        try {
            String key = CACHE_KEY_PREFIX + md5(query);
            redisTemplate.opsForValue().set(key, response, Duration.ofHours(1));
        } catch (Exception ignored) {
        }
    }

    /**
     * 记录 Token 消耗。
     */
    public void recordTokenUsage(Long userId, int tokens) {
        try {
            String key = QUOTA_KEY_PREFIX + userId + ":" + today();
            redisTemplate.opsForValue().increment(key, tokens);
            redisTemplate.expire(key, Duration.ofDays(1));
        } catch (Exception ignored) {
        }
    }

    /**
     * 标记 provider 故障。
     */
    public void markFaulty(String provider) {
        try {
            String key = FAULT_KEY_PREFIX + provider;
            redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(5));
            log.warn("Provider marked faulty: {}", provider);
        } catch (Exception ignored) {
        }
    }

    private boolean isQuotaExceeded(Long userId) {
        try {
            String key = QUOTA_KEY_PREFIX + userId + ":" + today();
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value) >= aiProperties.getRouting().getDailyTokenQuota();
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private boolean isFaulty(String provider) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(FAULT_KEY_PREFIX + provider));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 启发式复杂度估算（无需 LLM 调用，零成本）。
     * 基于：消息长度 + 问号数量 + 比较关键词 + 条件关键词
     */
    private int estimateComplexity(String query) {
        int score = 1;
        if (query.length() > 20) score++;
        if (query.length() > 50) score++;
        if (query.contains("对比") || query.contains("比较") || query.contains("vs")) score++;
        if (query.contains("和") && query.contains("哪个")) score++;
        if (query.contains("推荐") || query.contains("适合")) score++;
        return Math.min(score, 5);
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
