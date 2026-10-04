package org.icedamericanomall.merchant.domain.repository;

import org.icedamericanomall.merchant.domain.model.shop.Shop;

import java.util.List;
import java.util.Optional;

/**
 * 店铺聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>寻址口径：{@code shopId}（{@code shop.id}，对外的店铺业务标识）；
 * 归属维度：{@code merchantId}（{@code seller.id}，一个商家多个店铺，
 * 业务唯一键为 {@code (merchant_id, shop_name)}）。
 *
 * <p>资料更新一律带业务归属条件（{@code id + merchant_id}），
 * 越权改写他人店铺在持久化边界兜底（§12.4 越权防范）。
 */
public interface ShopRepository {

    /** 新增店铺（申请开店的默认店铺）。 */
    Shop insert(Shop shop);

    /** 按店铺 ID 查询。 */
    Optional<Shop> findById(Long shopId);

    /** 查询商家默认店铺（{@code id} 最小者）。 */
    Optional<Shop> findDefaultByMerchantId(Long merchantId);

    /** 查询商家全部店铺（按 {@code id} 升序）。 */
    List<Shop> findByMerchantId(Long merchantId);

    /** 更新店铺资料（部分字段，null 忽略；{@code id + merchant_id} 双条件）。 */
    int updateProfile(Shop shop);

    /** 落库聚合状态迁移结果（合法性由 {@link Shop} 实体方法保证，此处只写状态列）。 */
    int updateStatus(Shop shop);
}
