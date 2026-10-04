package org.icedamericanomall.ai.infrastructure.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * V3.0.1: 语义路由辅助 —— 复杂度估算 + FAQ 语义缓存。
 * <p>
 * 模型实例本身由 {@code AiAgentConfig} 装配（DeepSeek 主模型 + Qwen 备用模型），
 * 本类不再自行构建模型；原先的 {@code route/recordTokenUsage/markFaulty} 等方法无生产调用方，
 * 已删除（配额与故障转移由后续迭代按需重建）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class ModelRouter {

    private static final String CACHE_KEY_PREFIX = "ai:cache:";

    /** FAQ 语义缓存 TTL。 */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;

    public ModelRouter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 读取语义缓存（FAQ 常见问题 MD5 键）。
     *
     * @param query 用户消息
     * @return 缓存的响应；null 表示未命中
     */
    public String getCachedResponse(String query) {
        try {
            return redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + md5(query));
        } catch (Exception e) {
            log.warn("Semantic cache read failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入语义缓存。
     *
     * @param query    用户消息
     * @param response AI 回答
     */
    public void cacheResponse(String query, String response) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + md5(query), response, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Semantic cache write failed: {}", e.getMessage());
        }
    }

    /**
     * 启发式复杂度估算（无需 LLM 调用，零成本）。
     * 基于：消息长度 + 比较关键词 + 条件关键词 + 推荐意图。
     *
     * @param query 用户消息
     * @return 复杂度 1-5
     */
    public int estimateComplexity(String query) {
        int score = 1;
        if (query.length() > 20) score++;
        if (query.length() > 50) score++;
        if (query.contains("对比") || query.contains("比较") || query.contains("vs")) score++;
        if (query.contains("和") && query.contains("哪个")) score++;
        if (query.contains("推荐") || query.contains("适合")) score++;
        return Math.min(score, 5);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("MD5 unavailable, fallback to hashCode: {}", e.getMessage());
            return Integer.toHexString(input.hashCode());
        }
    }
}
