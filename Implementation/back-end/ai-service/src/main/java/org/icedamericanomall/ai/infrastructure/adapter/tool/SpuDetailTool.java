package org.icedamericanomall.ai.infrastructure.adapter.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.springframework.stereotype.Component;

/**
 * V3.0.2: @Tool — 商品详情查询，供 Search/Compare Subagent 使用。
 * <p>
 * 仅依赖领域端口 {@link SpuDetailPort}，Feign 调用在基础设施层（防腐层）。
 *
 * <p><b>承载层变更对本工具不可见（2026-10-18「甲b」，契约 §4.14.5.2 I70）</b>：
 * 数据来源已由公开 P2 迁到内部面 {@code GET /internal/item/spu/{spuId}}（裸 CO、无
 * {@code Result} 信封），但本类只与端口打交道 —— 渲染文本用的 {@code name} 是领域对象
 * {@code SpuDetailInfo} 的字段（不进任何报文，§1.8④ 不改清单），故工具层零改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpuDetailTool {

    private final SpuDetailPort spuDetailPort;

    /**
     * 查询商品详情。
     *
     * @param spuId SPU <b>业务键</b>（32-hex 字符串，来源为搜索/列表响应的 {@code spu_id}；
     *              ADR-062 §决策 3：跨服务边界不收技术键）
     * @return 商品详情文本；商品不存在或查询失败返回提示文案
     */
    @Tool("获取指定商品的详细信息：名称、品牌、描述、属性参数、图片等。spuId 传搜索/列表结果中的 spu_id（32 位字符串）")
    public String getSpuDetail(String spuId) {
        try {
            SpuDetailInfo detail = spuDetailPort.getSpuDetail(spuId);
            if (detail == null) {
                return "未找到商品 ID=" + spuId;
            }
            return String.format("商品详情 — %s (ID:%s)\n品牌:%s\n描述:%s\n销量:%s\n属性:%s",
                    detail.name(), detail.spuId(), detail.brand(),
                    detail.description(), detail.soldCount(), detail.attributes());
        } catch (Exception e) {
            log.warn("SpuDetailTool error: {}", e.getMessage());
            return "查询商品详情失败，请稍后重试";
        }
    }
}
