package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SpuDetailClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SpuDetailCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品详情服务降级工厂 —— <b>fail-closed（抛异常）</b>。
 *
 * <p>看似是「展示型查询」，但按本包的判定标准它属于 <b>判别型查询</b>（② 类）：
 * 唯一调用方 {@code ai-service SpuDetailTool} 的判定逻辑是
 * {@code if (detail == null) return "未找到商品 ID=" + spuId;} ——
 * 即把「取不到」解释为业务事实「商品不存在」。
 *
 * <p>因此：
 * <ul>
 *   <li>若降级返回 {@code null}，AI 助手会对用户说「未找到该商品」—— 明明商品存在、
 *       只是服务抖动，属于错误信息污染。</li>
 *   <li>若降级抛异常，调用方 {@code catch (Exception e)} 分支返回
 *       「查询商品详情失败: ...」—— 语义正确，且能把故障透出给用户。</li>
 * </ul>
 *
 * <p><b>「甲b」变更（2026-10-18，契约 §4.14.5.2 I70）</b>：内部面不包 {@code Result} 信封，
 * 故本工厂的返回类型由 {@code Result<SpuDetailCO>} 改为裸 {@code SpuDetailCO}；
 * 语义（fail-closed）与判定理由一字未变 —— 承载层搬家不该顺手改降级口径。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SpuDetailClientFallback implements FallbackFactory<SpuDetailClient> {

    @Override
    public SpuDetailClient create(Throwable cause) {
        return new SpuDetailClient() {

            @Override
            public SpuDetailCO getSpuDetail(String spuId) {
                log.error("商品详情服务降级：查询失败（禁止降级为 null，AI 工具会误判为「商品不存在」）"
                        + " spuId={}", spuId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "商品服务暂不可用，请稍后重试");
            }
        };
    }
}
