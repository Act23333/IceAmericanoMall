package org.icedamericanomall.merchant.app.seller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.merchant.adapter.request.ShopDecorationReq;
import org.icedamericanomall.merchant.adapter.request.UpdateShopReq;
import org.icedamericanomall.merchant.adapter.response.ShopDecorationVO;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopDecorationRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 店铺查询与资料维护（应用层编排）—— 公开店铺页、商家自有店铺列表 / 维护、
 * 店铺装修（S1c/S1d，ADR-048）、遗留 sellerId → 默认店铺兼容解析（ADR-019）。
 *
 * <p>"默认店铺"取该商家 {@code shop.id} 最小者（存量商家迁移时唯一生成，
 * 后续新增店铺按 id 递增，故解析稳定）；公开可见性唯一条件为
 * {@code shop.status=NORMAL}。
 *
 * <p><b>粉丝数回填（ADR-057）</b>：契约 §4.3.1 的 {@code ShopVO.follower_count} 取本地
 * Redis Set 基数（与 SF4 {@code GET /api/shop/follow/count} 同源），列表路径按店铺 ID 集合
 * <b>一次</b> pipeline 批量读（无 N 次往返）；Redis 异常时该字段为 null 而店铺照常返回
 * （fail-open，绝不因展示字段让店铺页/商家后台失败）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopManager {

    private static final String MERCHANT_NOT_OPEN = "您尚未开通商家";

    private final ShopRepository shopRepository;
    private final MerchantRepository merchantRepository;
    private final ShopDecorationRepository shopDecorationRepository;
    private final StoreFollowStore storeFollowStore;

    /**
     * 公开店铺页（新规范路径 {@code GET /api/shops/{shopId}}，SH1）。
     *
     * <p>V5.12 增量：装配店铺装修 {@code decoration}（公告/轮播/推荐商品位），
     * 未装修为 {@code null}（ADR-048，直读无缓存）。
     *
     * @throws BizException 店铺不存在或非正常状态（SHOP_NOT_FOUND）
     */
    public ShopVO getVisibleShop(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
        if (!shop.isNormal()) {
            throw new BizException(ErrorCode.SHOP_NOT_FOUND);
        }
        ShopVO vo = ShopVO.from(shop, loadMerchant(shop.getMerchantId()).getUserId());
        vo.setDecoration(shopDecorationRepository.findByShopId(shopId)
                .map(ShopDecorationVO::from)
                .orElse(null));
        enrichFollowerCounts(List.of(vo));
        return vo;
    }

    /**
     * 遗留兼容：{@code sellerId}（商家 user_id）→ 默认店铺 → 店铺视图。
     *
     * @deprecated 改用 {@link #getVisibleShop(Long)}（shopId 口径），
     *         本方法仅为旧前端 {@code GET /api/shop/{sellerId}} 保留。
     */
    @Deprecated
    public ShopVO getLegacyShopBySellerId(Long sellerId) {
        return getVisibleShop(resolveDefaultShopId(sellerId));
    }

    /**
     * 解析目标店铺 ID：{@code shopId} 优先，其次遗留 {@code sellerId} → 默认店铺。
     *
     * @throws BizException 两者皆未提供（PARAM_ERROR）或店铺不存在（SHOP_NOT_FOUND）
     */
    public Long resolveShopId(Long shopId, Long sellerId) {
        if (shopId != null) {
            shopRepository.findById(shopId).orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
            return shopId;
        }
        if (sellerId != null) {
            return resolveDefaultShopId(sellerId);
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "shopId 与 sellerId 至少提供一个");
    }

    /**
     * 商家本人店铺列表。
     *
     * @throws BizException 当前用户尚未开通商家（USER_NOT_FOUND）
     */
    public List<ShopVO> listMyShops(Long userId) {
        Merchant merchant = loadMerchantByUserId(userId);
        List<ShopVO> shops = shopRepository.findByMerchantId(merchant.getId()).stream()
                .map(shop -> ShopVO.from(shop, merchant.getUserId()))
                .toList();
        enrichFollowerCounts(shops);
        return shops;
    }

    /**
     * 更新指定店铺资料（校验归属：店铺必须属于当前用户，否则拒绝）。
     *
     * @throws BizException      店铺不存在（SHOP_NOT_FOUND）
     * @throws ForbiddenException 店铺归属其他商家（FORBIDDEN）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMyShop(Long userId, Long shopId, UpdateShopReq req) {
        Shop shop = loadOwnedShop(userId, shopId);
        shop.updateProfile(
                blankToNull(req.getShopName()),
                blankToNull(req.getShopLogo()),
                blankToNull(req.getContactPhone()),
                blankToNull(req.getProvince()),
                blankToNull(req.getCity()),
                blankToNull(req.getDistrict()),
                blankToNull(req.getDetailAddress()));
        shopRepository.updateProfile(shop);
    }

    /**
     * 遗留别名：当前商家的默认店铺。
     *
     * @deprecated 改用 {@link #listMyShops(Long)}（多店铺）或
     *         {@code GET /api/seller/shops/{shopId}}。
     */
    @Deprecated
    public ShopVO getMyDefaultShop(Long userId) {
        Merchant merchant = loadMerchantByUserId(userId);
        Shop shop = shopRepository.findDefaultByMerchantId(merchant.getId())
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
        ShopVO vo = ShopVO.from(shop, merchant.getUserId());
        enrichFollowerCounts(List.of(vo));
        return vo;
    }

    /**
     * 遗留别名：更新默认店铺资料。
     *
     * @deprecated 改用 {@link #updateMyShop(Long, Long, UpdateShopReq)}。
     */
    @Deprecated
    public void updateMyDefaultShop(Long userId, UpdateShopReq req) {
        Merchant merchant = loadMerchantByUserId(userId);
        Shop shop = shopRepository.findDefaultByMerchantId(merchant.getId())
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
        updateMyShop(userId, shop.getId(), req);
    }

    // ==================== 店铺装修（S1c/S1d，ADR-048） ====================

    /**
     * 店铺装修查询（{@code GET /api/seller/shops/{shopId}/decoration}，S1c）。
     *
     * <pre>
     * Scenario: 商家查看自有店铺装修
     *   Given 商家已登录且目标店铺归属该商家
     *   When GET /api/seller/shops/{shopId}/decoration
     *   Then 返回 ShopDecorationVO；未装修时各字段为空值 / 空数组
     * </pre>
     *
     * @throws BizException       尚未开通商家（USER_NOT_FOUND）或店铺不存在（SHOP_NOT_FOUND）
     * @throws ForbiddenException 店铺归属其他商家（FORBIDDEN）
     */
    public ShopDecorationVO getDecoration(Long userId, Long shopId) {
        loadOwnedShop(userId, shopId);
        return shopDecorationRepository.findByShopId(shopId)
                .map(ShopDecorationVO::from)
                .orElseGet(ShopDecorationVO::empty);
    }

    /**
     * 店铺装修保存（{@code PUT /api/seller/shops/{shopId}/decoration}，S1d）。
     *
     * <p>PUT 全量替换语义：未传字段按空保存（发送空列表即清空对应装修项）；
     * 落库为 uk_shop 单语句 UPSERT；内容校验（公告 ≤200 / 轮播 ≤5 且 http(s)、
     * 推荐位 ≤8 去重）收口在领域 {@link ShopDecoration}；
     * hotProductIds Phase 1 不校验商品归属（展示端过滤，ADR-048）。
     *
     * <pre>
     * Scenario: 商家保存店铺装修
     *   Given 商家已登录且目标店铺归属该商家
     *   When PUT /api/seller/shops/{shopId}/decoration with ShopDecorationReq
     *   Then 校验通过并 UPSERT（首次插入 / 再次保存整体替换），返回成功
     * </pre>
     *
     * @throws BizException       店铺不存在（SHOP_NOT_FOUND）或装修内容不合法（PARAM_ERROR）
     * @throws ForbiddenException 店铺归属其他商家（FORBIDDEN）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDecoration(Long userId, Long shopId, ShopDecorationReq req) {
        loadOwnedShop(userId, shopId);
        shopDecorationRepository.save(ShopDecoration.of(
                shopId, req.getAnnouncement(), req.getBanners(), req.getHotProductIds()));
    }

    // ==================== 内部方法 ====================

    /**
     * 批量回填粉丝数（契约 {@code ShopVO.follower_count}，ADR-057）。
     *
     * <p><b>批量</b>：去重后的店铺 ID 集合只打<b>一次</b> Redis 端口（实现为 pipeline，
     * 一次网络往返），禁止逐店铺查询（N+1）。
     *
     * <p><b>fail-open</b>：Redis 异常 → 记 warn 后原样返回（粉丝数字段保持 {@code null}，
     * 不回落为 0）；端口返回缺失店铺 → 该店铺保持 {@code null}。两种情况都<b>不</b>抛异常。
     */
    private void enrichFollowerCounts(List<ShopVO> shops) {
        if (shops == null || shops.isEmpty()) {
            return;
        }
        List<Long> shopIds = shops.stream()
                .map(ShopVO::getShopId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (shopIds.isEmpty()) {
            return;
        }
        Map<Long, Long> counts;
        try {
            counts = storeFollowStore.followerCounts(shopIds);
        } catch (Exception e) {
            log.warn("店铺粉丝数回填失败，follower_count 置空（fail-open）: shopIds={}, cause={}",
                    shopIds, e.getMessage());
            return;
        }
        if (counts == null || counts.isEmpty()) {
            return;
        }
        for (ShopVO vo : shops) {
            if (vo.getShopId() != null) {
                vo.setFollowerCount(counts.get(vo.getShopId()));
            }
        }
    }

    /** 加载当前用户名下的店铺（不存在 → SHOP_NOT_FOUND；归属他人 → FORBIDDEN）。 */
    private Shop loadOwnedShop(Long userId, Long shopId) {
        Merchant merchant = loadMerchantByUserId(userId);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
        if (!merchant.getId().equals(shop.getMerchantId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权操作该店铺");
        }
        return shop;
    }

    /** sellerId（商家 user_id）→ 默认店铺 ID。 */
    private Long resolveDefaultShopId(Long sellerId) {
        Merchant merchant = merchantRepository.findByUserId(sellerId)
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
        return shopRepository.findDefaultByMerchantId(merchant.getId())
                .map(Shop::getId)
                .orElseThrow(() -> new BizException(ErrorCode.SHOP_NOT_FOUND));
    }

    private Merchant loadMerchantByUserId(Long userId) {
        return merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, MERCHANT_NOT_OPEN));
    }

    private Merchant loadMerchant(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "商家不存在"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
