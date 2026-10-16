package org.icedamericanomall.user.app.port;

import org.icedamericanomall.user.app.dto.SpuBriefView;

import java.util.Collection;
import java.util.Map;

/**
 * 商品展示摘要查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>存在理由</b>：收藏表（{@code favorite}）只存 {@code spu_id}，而契约 §4.2.6 U20 的
 * {@code FavoriteVO} 必须投影 {@code product_name} / {@code main_image} / {@code price} / {@code status}
 * （05-API-Specification 为唯一真相源）。商品域数据不属于收藏聚合，故以独立读模型端口获取，
 * <b>不污染</b> {@code Favorite} 聚合（聚合只以 ID 引用其他聚合）。
 *
 * <p><b>为何按 ID 批量</b>：收藏列表一页 N 行只能打一次数据源，禁止逐行查询（N+1）。
 *
 * <p><b>边界说明（架构债，已在交付报告中登记）</b>：当前基础设施实现为同库读模型 JOIN
 * （与同服务 {@code HistoryViewQuery} 足迹读模型同一口径、同一批 item 域表）。这与
 * ADR-052「跨域数据经 {@code /internal/**} 出口」的方向存在张力——隔离点即本端口：
 * 后续如需退出直读，只需新增 item-service 批量商品摘要内部端点 +
 * {@code ia-api} Feign 客户端，替换本端口的实现类即可，应用层与适配层零改动。
 */
public interface FavoriteSpuBriefQuery {

    /**
     * 按商品 ID 批量查询展示摘要。
     *
     * @param spuIds 商品 ID 集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return 商品 ID → 摘要；不存在的商品（已删除等）<b>不出现</b>在返回 Map 中
     */
    Map<Long, SpuBriefView> findByIds(Collection<Long> spuIds);
}
