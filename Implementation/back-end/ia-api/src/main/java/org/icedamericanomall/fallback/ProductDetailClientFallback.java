package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.ProductDetailClient;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.ProductDetailCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品详情服务降级工厂 —— <b>fail-closed（抛异常）</b>。
 *
 * <p>看似是「展示型查询」，但按本包的判定标准它属于 <b>判别型查询</b>（② 类）：
 * 唯一调用方 {@code ai-service ProductDetailTool} 的判定逻辑是
 * {@code if (result == null || result.getData() == null) return "未找到商品 ID=" + productId;}
 * —— 即把「{@code data} 为 null」解释为业务事实「商品不存在」。
 *
 * <p>因此：
 * <ul>
 *   <li>若降级返回 {@code null} 或 {@code Result.error(...)}（{@code data} 同为 null），
 *       AI 助手会对用户说「未找到该商品」—— 明明商品存在、只是服务抖动，属于错误信息污染。</li>
 *   <li>若降级抛异常，调用方 {@code catch (Exception e)} 分支返回
 *       「查询商品详情失败: ...」—— 语义正确，且能把故障透出给用户。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class ProductDetailClientFallback implements FallbackFactory<ProductDetailClient> {

    @Override
    public ProductDetailClient create(Throwable cause) {
        return new ProductDetailClient() {

            @Override
            public Result<ProductDetailCO> getProductDetail(Long id) {
                log.error("商品详情服务降级：查询失败（禁止降级为 data=null，AI 工具会误判为「商品不存在」）"
                        + " productId={}", id, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "商品服务暂不可用，请稍后重试");
            }
        };
    }
}
