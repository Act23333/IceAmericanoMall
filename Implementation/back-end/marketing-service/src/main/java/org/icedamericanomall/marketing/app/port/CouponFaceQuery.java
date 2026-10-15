package org.icedamericanomall.marketing.app.port;

import org.icedamericanomall.marketing.app.dto.CouponFace;

import java.util.Collection;
import java.util.Map;

/**
 * 优惠券券面读模型查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>为何单独走读模型</b>：{@code user_coupon} 与 {@code coupon} 分属两个聚合 ——
 * 持券聚合只以 ID 引用券模板（ADR-025），券面三列属券模板聚合。本端口即这一跨聚合读数的
 * 隔离点（ADR-057：端口在 {@code app/port/}，实现在 {@code infrastructure/persistence/}，
 * 不外泄到适配层）。
 *
 * <p><b>为何按 ID 批量</b>：我的券列表一页 N 行只允许打一次数据源，禁止逐行查询（N+1）。
 *
 * <p><b>为何不引入 Feign</b>：{@code coupon} 表与 {@code user_coupon} 同库（marketing 自有
 * schema），本地只读即可满足批量与事务合规（ADR-057：{@code @Transactional} 的 Manager
 * 只能调本地只读实现）。
 *
 * <p><b>fail-open 归属</b>：本端口不吞异常（真实故障需在调用侧可见）；由调用方
 * {@code UserCouponViewAssembler} 兜住异常并把三字段留空 —— 券面取不到绝不能让券列表失败。
 */
public interface CouponFaceQuery {

    /**
     * 按券模板 ID 批量查询券面信息。
     *
     * @param couponIds 券模板主键集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return 券模板 ID → 券面；<b>模板已删除时该键不出现</b>（调用方按缺失把三字段留空，
     *         持券行照常返回）
     */
    Map<Long, CouponFace> findByCouponIds(Collection<Long> couponIds);
}
