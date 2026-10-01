package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SearchClient;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 搜索服务降级工厂 —— <b>展示型查询，fail-soft（返回非成功 Result）</b>。
 *
 * <p>为什么这里可以（且应该）返回空值而不是抛异常：
 * <ul>
 *   <li>两个调用方 {@code ai-service SearchTool} 与 {@code RAGRetriever} 都把
 *       {@code result == null || result.getData() == null} 明确解释为
 *       <b>「搜索服务暂不可用」</b>—— 与下游故障的语义一致，不存在错误信息污染。</li>
 *   <li>搜索是 AI 助手的「锦上添花」能力：降级后助手仍可基于其它工具回答用户，
 *       抛异常只会让整轮对话失败。</li>
 * </ul>
 *
 * <p>实现细节：返回 {@link Result#error(int, String)} 而非 {@code Result.ok(null)} ——
 * 后者会谎报「成功但结果为空」，前者在 {@code data} 同为 {@code null} 的前提下
 * 额外保留了 {@code code/message}，任何未来检查 {@code code} 的调用方也能正确识别故障。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SearchClientFallback implements FallbackFactory<SearchClient> {

    @Override
    public SearchClient create(Throwable cause) {
        return new SearchClient() {

            @Override
            public Result<Map<String, Object>> searchProducts(String keyword, int size) {
                log.warn("搜索服务降级：关键词检索失败，本次降级为空结果 keyword={}, size={}",
                        keyword, size, cause);
                return Result.error(ErrorCode.SYSTEM_BUSY.getCode(), "搜索服务暂不可用");
            }
        };
    }
}
