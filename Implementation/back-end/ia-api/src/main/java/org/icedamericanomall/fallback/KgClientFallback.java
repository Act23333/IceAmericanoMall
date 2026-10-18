package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.KgClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.KgEdgeUpsertCO;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 知识图谱检索面降级工厂 —— <b>fail-open（纯旁路增强）</b>，但<b>不是无差别 fail-open</b>：
 * 按调用点逐个定类（依据=读调用方所得）。
 *
 * <table border="1">
 *   <caption>逐方法定类</caption>
 *   <tr><th>方法</th><th>调用方 / 包裹</th><th>处置</th></tr>
 *   <tr><td>{@code edgeUpsert}</td>
 *       <td>{@code item KgEdgeIngestManager#upsertSafely}（afterCommit 旁路，自带 try/catch）</td>
 *       <td>4xx 透传；{@code TRANSIENT}/{@code BUG} → 哨兵 {@code -1}</td></tr>
 *   <tr><td>{@code deleteEdges}</td>
 *       <td>{@code item KgEdgeIngestManager#deleteEdgesSafely}（自带 try/catch）</td>
 *       <td>4xx 透传；其余 → 抛 {@code SYSTEM_BUSY}（<b>不是空操作</b>：静默返回让调用方以为清边成功，
 *           残留边会一直挂在图谱里；抛错由调用点 catch，主流程行为不变）</td></tr>
 *   <tr><td><b>{@code neighbors}</b></td>
 *       <td>{@code item KgPairingManager#itemsFromGraph} —— <b>没有 try/catch</b>，
 *           直接服务于 C 端商品详情 P35 搭配卡；空列表即回退向量兜底</td>
 *       <td><b>一律 fail-open 空列表</b>（4xx 也不抛）—— PRD §16.8.1「全链路 fail-open」
 *           是最高优先级纪律；此处的「空」不是伪装的业务事实（搭配位可空是接受的产品形态），
 *           抛错会把一次商品详情变成 500</td></tr>
 * </table>
 *
 * <p>定类对齐 ADR-046「推荐纯旁路全链路 fail-open」与 I47 哨兵先例：
 * {@code -1} = 本批边未投影（调用方据此不打「已投影」日志），空列表 = 无邻居。
 */
@Slf4j
@Component
public class KgClientFallback implements FallbackFactory<KgClient> {

    /** 摄取降级哨兵（对齐 I47 口径）。 */
    public static final int UPSERT_FALLBACK_SENTINEL = -1;

    /** {@code deleteEdges} 的降级文案 —— 「暂时无法执行」，<b>不得</b>写成「服务不可用」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "知识图谱暂时无法更新，请稍后重试";

    @Override
    public KgClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new KgClient() {

            @Override
            public int edgeUpsert(KgEdgeUpsertCO upsert) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("KG 边摄取降级(透传): 下游业务失败 srcType={}, srcId={}, code={}, msg={}",
                            upsert == null ? null : upsert.getSrcType(),
                            upsert == null ? null : upsert.getSrcId(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("KG 边摄取降级：暂时无法获取，本批边未投影（哨兵 -1，漏投影由 AD64 全量重建兜底）"
                                + " srcType={}, srcId={}, edgeCount={}, category={}",
                        upsert == null ? null : upsert.getSrcType(),
                        upsert == null ? null : upsert.getSrcId(),
                        upsert == null || upsert.getEdges() == null ? 0 : upsert.getEdges().size(),
                        category, cause);
                return UPSERT_FALLBACK_SENTINEL;
            }

            @Override
            public List<KgNeighborCO> neighbors(String srcType, Long srcId, String relType,
                                                String dstType, Integer depth, Integer status) {
                // ★ 本条不做 4xx 透传：唯一调用方（KgPairingManager）无 try/catch，抛错会打断
                //    商品详情；PRD §16.8.1 明确「全链路 fail-open」。空列表 → 回退向量兜底。
                log.warn("KG 邻居查询降级：暂时无法获取，返回空列表（PRD §16.8.1 全链路 fail-open，"
                                + "搭配位回退向量兜底） srcType={}, srcId={}, category={}",
                        srcType, srcId, category, cause);
                return Collections.emptyList();
            }

            @Override
            public void deleteEdges(String srcType, Long srcId) {
                // 4xx 透传（必须真的抛出去）；其余抛 SYSTEM_BUSY。
                // 唯一调用方 item KgEdgeIngestManager#deleteEdgesSafely:179 自带 try/catch(Exception)，
                // 抛错不打断主流程 —— 即「依然 fail-open，但不再谎报成功」。
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("KG 清边降级(透传): 下游业务失败 srcType={}, srcId={}, code={}, msg={}",
                            srcType, srcId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("KG 清边降级: 本次清边未执行（category={}；⚠ 无可靠消费者——重建只覆盖「仍存在且已下架」"
                                + "的 SPU，已删除 SPU 的残留边需人工处理） srcType={}, srcId={}",
                        category, srcType, srcId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
