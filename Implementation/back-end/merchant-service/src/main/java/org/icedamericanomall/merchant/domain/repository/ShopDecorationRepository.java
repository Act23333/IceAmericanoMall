package org.icedamericanomall.merchant.domain.repository;

import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;

import java.util.Optional;

/**
 * 店铺装修仓储（领域层定义，基础设施层实现）。
 *
 * <p>寻址口径：{@code shopId}（业务唯一键 {@code uk_shop}，装修与 shop 1:1，ADR-048）。
 * 按业务唯一键读写，不暴露技术主键操作。
 */
public interface ShopDecorationRepository {

    /** 按店铺 ID 查询装修（未装修返回 empty）。 */
    Optional<ShopDecoration> findByShopId(Long shopId);

    /**
     * 保存装修（UPSERT）—— 依赖库表唯一索引 {@code uk_shop} 的
     * {@code INSERT ... ON DUPLICATE KEY UPDATE} 单语句原子写入：
     * 首次保存插入、再次保存整体替换（PUT 全量语义）。
     *
     * <p>选型说明：不用"先查后写"——并发首次保存会同时判空并插入，
     * 仍需唯一键冲突兜底，单语句 UPSERT 天然幂等且少一次往返
     * （与 wallet {@code WalletAccountMapper.credit} 同款）。
     *
     * @param decoration 已通过领域校验的装修聚合（JSON 序列化在仓储实现内完成）
     */
    void save(ShopDecoration decoration);
}
