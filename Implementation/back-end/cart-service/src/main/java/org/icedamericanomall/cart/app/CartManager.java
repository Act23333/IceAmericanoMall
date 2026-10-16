package org.icedamericanomall.cart.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.cart.adapter.assembler.CartConverter;
import org.icedamericanomall.cart.adapter.request.CartAddReq;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.adapter.response.CartVO;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.dto.SkuCO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车编排（应用层）。
 *
 * <p>职责：购物车用例编排（事务边界）+ 跨服务（item-service）批量回填 SKU 信息与金额计算。
 * 购物车表不存商品快照，商品名/规格/单价/图片均在读时回填；Feign 调用只允许出现在本层。
 * 回填属<b>纯展示字段</b>，按 ADR-057 fail-open：item-service 不可用时列表照常返回、
 * 字段留空（见 {@link #loadSkuMap}）；下单计价/扣库存链路另行 fail-closed。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartManager {

    private final CartRepository cartRepository;
    private final SkuClient skuClient;
    private final CartConverter cartConverter;

    /**
     * 购物车视图：一次批量查询回填 SKU 信息，并计算小计 / 总价 / 选中总价 / 全选。
     *
     * <p>全选语义：购物车非空、每项均已选中且每项都能取到单价（SKU 下架或不存在时不得为 true）。
     */
    public CartVO getCartView(Long userId) {
        List<CartItem> items = cartRepository.findByUserIdOrderByCreateTimeDesc(userId);
        if (items.isEmpty()) {
            CartVO empty = new CartVO();
            empty.setItems(List.of());
            empty.setTotalPrice(0);
            empty.setSelectedPrice(0);
            empty.setAllSelected(false);
            return empty;
        }

        CartVO cartVO = new CartVO();
        List<CartItemVO> itemVOs = cartConverter.itemsToVOs(items);
        cartVO.setItems(itemVOs);

        Map<Long, SkuCO> skuMap = loadSkuMap(itemVOs);
        int totalPrice = 0;
        int selectedPrice = 0;
        boolean allSelected = true;
        for (CartItemVO item : itemVOs) {
            enrich(item, skuMap.get(item.getSkuId()));
            if (item.getPrice() == null) {
                // SKU 已下架/不存在：该项无法计价，不计入总价，也不允许标记全选
                item.setSubTotal(0);
                allSelected = false;
                continue;
            }
            totalPrice += item.getSubTotal();
            if (Boolean.TRUE.equals(item.getSelected())) {
                selectedPrice += item.getSubTotal();
            } else {
                allSelected = false;
            }
        }
        cartVO.setTotalPrice(totalPrice);
        cartVO.setSelectedPrice(selectedPrice);
        cartVO.setAllSelected(allSelected);
        return cartVO;
    }

    /**
     * <pre>
     * Scenario: 添加商品到购物车（去重合并）
     *   Given 用户已登录
     *   When POST /api/cart/item with {skuId, quantity}
     *   Then 若购物车中不存在同一 SKU，则新增记录（默认选中）
     *   And 若已存在同一 SKU，则原子累加数量（防重复）
     *
     * Scenario: 并发加购撞唯一键
     *   Given 两个请求同时判定「不存在该 SKU」
     *   When 后插入者撞上 uk_user_sku
     *   Then 捕获 DuplicateKeyException 转为增量合并，不返回 500
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddReq req) {
        var existing = cartRepository.findByUserIdAndSkuId(userId, req.getSkuId());
        if (existing.isPresent()) {
            cartRepository.incrementQuantity(existing.get().getId(), req.getQuantity());
            return;
        }
        try {
            cartRepository.save(CartItem.create(userId, req.getSkuId(), req.getQuantity()));
        } catch (DuplicateKeyException e) {
            // 并发场景：另一请求已插入同一 (user_id, sku_id)，改为读最新行后增量合并
            var concurrent = cartRepository.findByUserIdAndSkuId(userId, req.getSkuId());
            if (concurrent.isEmpty()) {
                throw e;
            }
            log.info("并发加购命中唯一键，转为增量合并: userId={}, skuId={}", userId, req.getSkuId());
            cartRepository.incrementQuantity(concurrent.get().getId(), req.getQuantity());
        }
    }

    /**
     * <pre>
     * Scenario: 修改购物车商品数量
     *   When PUT /api/cart/item with {skuId, quantity}
     *   Then 更新该购物车项的数量
     * Scenario: 数量 ≤ 0 则删除该项
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long userId, CartUpdateReq req) {
        var existing = cartRepository.findByUserIdAndSkuId(userId, req.getSkuId());
        if (existing.isEmpty()) {
            return;
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            cartRepository.delete(existing.get().getId());
            return;
        }
        cartRepository.updateQuantity(existing.get().getId(), req.getQuantity());
    }

    /** 移除购物车项。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long skuId) {
        cartRepository.deleteByUserIdAndSkuId(userId, skuId);
    }

    /** 切换选中状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void selectItem(Long userId, Long skuId, Boolean selected) {
        cartRepository.updateSelected(userId, skuId, selected);
    }

    /** 清空购物车。 */
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartRepository.clearByUserId(userId);
    }

    /** 选中项查询（下单取数，internal）。 */
    public List<CartItemCO> getSelectedItemCos(Long userId) {
        return cartConverter.itemsToCOs(cartRepository.findSelectedByUserId(userId));
    }

    /** V4.0: 删除指定购物车项（仅删除已下单的商品，京东标准）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Long userId, List<Long> cartItemIds) {
        cartRepository.deleteByIds(userId, cartItemIds);
    }

    // ==================== 私有方法 ====================

    /**
     * 批量查询 SKU（一次 Feign 调用，禁止逐项查询）。
     *
     * <p><b>fail-open（ADR-057 展示字段口径）</b>：商品名/规格/单价/图片在购物车是<b>纯展示回填</b>
     * （见类注释），item-service 不可用时必须让购物车列表照常返回，故降级为空 {@code Map} + WARN；
     * 逐项后果由 {@link #getCartView} 现有分支承接（单价为空 → 该项小计 0、不计入总价、
     * {@code allSelected=false}），前端仍可渲染「暂不可用」并提示重试。
     * <p>与下单链路刻意不同：I13 在 {@code NormalCartOrderStrategy} 里是<b>判别型查询</b>
     * （空列表 = SKU 不存在），必须 fail-closed —— 扣库存/计价绝不允许静默降级。
     */
    private Map<Long, SkuCO> loadSkuMap(List<CartItemVO> items) {
        List<Long> skuIds = items.stream()
                .map(CartItemVO::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        List<SkuCO> skus;
        try {
            skus = skuClient.getSkuListByIds(skuIds);
        } catch (Exception e) {
            // 展示回填失败不阻断列表：降级为空 Map（不回填、不计价），仅告警
            log.warn("购物车商品回填失败，展示字段置空（fail-open）: skuIds={}, cause={}",
                    skuIds, e.getMessage());
            return Map.of();
        }
        if (skus == null || skus.isEmpty()) {
            log.warn("购物车商品回填为空，可能存在已下架或已删除的 SKU: skuIds={}", skuIds);
            return Map.of();
        }
        return skus.stream()
                .filter(sku -> sku.getId() != null)
                .collect(Collectors.toMap(SkuCO::getId, Function.identity(), (a, b) -> a));
    }

    /** 用 SKU 信息回填购物车项并计算小计（分）。 */
    private void enrich(CartItemVO item, SkuCO sku) {
        if (sku == null) {
            return;
        }
        item.setProductName(sku.getProductName());
        item.setSpec(sku.getSpec());
        item.setImage(sku.getImage());
        item.setPrice(sku.getPrice());
        item.setSubTotal(sku.getPrice() == null || item.getQuantity() == null
                ? 0 : sku.getPrice() * item.getQuantity());
    }
}
