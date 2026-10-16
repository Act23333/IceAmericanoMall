package org.icedamericanomall.cart.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.cart.adapter.assembler.CartConverter;
import org.icedamericanomall.cart.adapter.request.CartAddReq;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.adapter.response.CartVO;
import org.icedamericanomall.cart.app.dto.SkuView;
import org.icedamericanomall.cart.app.port.CartSkuViewQuery;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CartItemCO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 购物车编排（应用层）。
 *
 * <p>职责：购物车用例编排（事务边界）+ SKU 视图回填（业务键解析 / 展示字段 / 金额计算）。
 * 购物车表不存商品快照，商品名/规格/单价/图片与 <b>SKU 业务键</b>均在读时回填。
 *
 * <p><b>标识口径（ADR-061 §决策 2 + A 案）</b>：线格式（{@code CartItemVO.sku_id}、
 * C2/C4/C5 入参、{@code CartItemCO.skuId}）一律为 <b>SKU 业务键</b> 32-hex；
 * 而 {@code cart.sku_id} 是 {@code sku.id} 的<b>同库技术外键，保持不动</b>（A 案）。
 * 两个方向的解析共用<b>同一次</b>只读批量查询（{@link CartSkuViewQuery}，
 * 走 {@code sku.sku_id} 唯一索引，无 N+1、无额外往返）：
 * <ul>
 *   <li><b>入参方向</b>：业务键 → 技术键（加购/改数量/删除/改选落库前）；</li>
 *   <li><b>出参方向</b>：技术键 → 业务键（列表 / 下单取数回填报文）。</li>
 * </ul>
 *
 * <p><b>fail-open / fail-closed 分界（不得混淆）</b>：
 * <ul>
 *   <li><b>列表回填（读）fail-open</b>（ADR-057）：数据源异常 → 字段留空 + WARN，
 *       列表照常返回；{@code sku_id} 亦留空（<b>不回落技术键</b>，ADR-061 §决策 3）。</li>
 *   <li><b>加购（写）fail-closed</b>：业务键解析不到 → {@code SKU_NOT_FOUND}，
 *       绝不静默插入一条无法下单的行。</li>
 *   <li><b>改数量/删除/改选</b>：解析不到即<b>幂等空操作</b>（目标行本就不存在，
 *       与「删除不存在的购物车项」同语义，不产生 500）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartManager {

    private final CartRepository cartRepository;
    private final CartSkuViewQuery cartSkuViewQuery;
    private final CartConverter cartConverter;

    /**
     * 购物车视图：一次批量查询回填 SKU 信息，并计算小计 / 总价 / 选中总价 / 全选。
     *
     * <p>全选语义：购物车非空、每项均已选中且每项都能取到单价（SKU 下架或不存在时不得为 true）。
     * <p>{@code CartItemVO.sku_id} 出<b>业务键</b>（ADR-061）：同一次结果带出，
     * 与 C4/C5 入参同型同义；查不到时为 {@code null}（fail-open，不回落技术键）。
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

        Map<Long, SkuView> byTechnicalId = loadByTechnicalIds(technicalIdsOf(items));
        int totalPrice = 0;
        int selectedPrice = 0;
        boolean allSelected = true;
        for (int i = 0; i < items.size(); i++) {
            CartItemVO item = itemVOs.get(i);
            enrich(item, byTechnicalId.get(items.get(i).getSkuId()));
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
     *   When POST /api/cart/item with {sku_id(业务键), quantity}
     *   Then 若购物车中不存在同一 SKU，则新增记录（默认选中）
     *   And 若已存在同一 SKU，则原子累加数量（防重复）
     *
     * Scenario: 并发加购撞唯一键
     *   Given 两个请求同时判定「不存在该 SKU」
     *   When 后插入者撞上 uk_user_sku
     *   Then 捕获 DuplicateKeyException 转为增量合并，不返回 500
     *
     * Scenario: SKU 业务键解析不到
     *   When 传非法业务键或 SKU 不存在
     *   Then 抛 SKU_NOT_FOUND（fail-closed，绝不静默插入无法下单的行）
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long userId, CartAddReq req) {
        Long skuTechnicalId = resolveTechnicalId(req.getSkuId());
        var existing = cartRepository.findByUserIdAndSkuId(userId, skuTechnicalId);
        if (existing.isPresent()) {
            cartRepository.incrementQuantity(existing.get().getId(), req.getQuantity());
            return;
        }
        try {
            cartRepository.save(CartItem.create(userId, skuTechnicalId, req.getQuantity()));
        } catch (DuplicateKeyException e) {
            // 并发场景：另一请求已插入同一 (user_id, sku_id)，改为读最新行后增量合并
            var concurrent = cartRepository.findByUserIdAndSkuId(userId, skuTechnicalId);
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
     *   When PUT /api/cart/item with {sku_id(业务键), quantity}
     *   Then 更新该购物车项的数量
     * Scenario: 数量 ≤ 0 则删除该项
     * Scenario: SKU 业务键解析不到 → 幂等空操作（行本就不存在）
     * </pre>
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long userId, CartUpdateReq req) {
        Long skuTechnicalId = resolveTechnicalIdOrNull(req.getSkuId());
        if (skuTechnicalId == null) {
            return;
        }
        var existing = cartRepository.findByUserIdAndSkuId(userId, skuTechnicalId);
        if (existing.isEmpty()) {
            return;
        }
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            cartRepository.delete(existing.get().getId());
            return;
        }
        cartRepository.updateQuantity(existing.get().getId(), req.getQuantity());
    }

    /** 移除购物车项（入参为 SKU 业务键；解析不到 → 幂等空操作）。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, String skuId) {
        Long skuTechnicalId = resolveTechnicalIdOrNull(skuId);
        if (skuTechnicalId == null) {
            return;
        }
        cartRepository.deleteByUserIdAndSkuId(userId, skuTechnicalId);
    }

    /** 切换选中状态（入参为 SKU 业务键；解析不到 → 幂等空操作）。 */
    @Transactional(rollbackFor = Exception.class)
    public void selectItem(Long userId, String skuId, Boolean selected) {
        Long skuTechnicalId = resolveTechnicalIdOrNull(skuId);
        if (skuTechnicalId == null) {
            return;
        }
        cartRepository.updateSelected(userId, skuTechnicalId, selected);
    }

    /** 清空购物车。 */
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartRepository.clearByUserId(userId);
    }

    /**
     * 选中项查询（下单取数，internal）。
     *
     * <p>{@code CartItemCO.skuId} 出<b>业务键</b>（ADR-061：与 I13 {@code sku_ids} 入参口径
     * 同型同义）—— 由同一次批量只读查询回填（{@code cart.sku_id} → 业务键），
     * trade 侧 {@code NormalCartOrderStrategy} 据此与 I13 返回的 {@code SkuCO} 对齐。
     * 回填缺失时该字段为 {@code null}（fail-open，不回落技术键）——下单策略按
     * 「返回条数 != 请求条数」判定缺项并 fail-closed，故不会静默漏商品。
     */
    public List<CartItemCO> getSelectedItemCos(Long userId) {
        List<CartItem> items = cartRepository.findSelectedByUserId(userId);
        List<CartItemCO> cos = cartConverter.itemsToCOs(items);
        if (items.isEmpty()) {
            return cos;
        }
        Map<Long, SkuView> byTechnicalId = loadByTechnicalIds(technicalIdsOf(items));
        for (int i = 0; i < items.size() && i < cos.size(); i++) {
            SkuView sku = byTechnicalId.get(items.get(i).getSkuId());
            if (sku != null) {
                cos.get(i).setSkuId(sku.skuBusinessId());
            }
        }
        return cos;
    }

    /** V4.0: 删除指定购物车项（仅删除已下单的商品，京东标准）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Long userId, List<Long> cartItemIds) {
        cartRepository.deleteByIds(userId, cartItemIds);
    }

    // ==================== 私有方法 ====================

    /**
     * SKU 业务键 → 技术键；解析不到即抛 {@code SKU_NOT_FOUND}（fail-closed）。
     *
     * @throws BizException 业务键格式非法 / SKU 不存在
     */
    private Long resolveTechnicalId(String skuBusinessId) {
        SkuView sku = loadByBusinessIds(List.of(skuBusinessId)).get(skuBusinessId);
        if (sku == null || sku.skuTechnicalId() == null) {
            log.warn("SKU 业务键解析失败（fail-closed）: skuId={}", skuBusinessId);
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku.skuTechnicalId();
    }

    /** SKU 业务键 → 技术键；解析不到返回 {@code null}（调用方按幂等空操作处理）。 */
    private Long resolveTechnicalIdOrNull(String skuBusinessId) {
        if (skuBusinessId == null) {
            return null;
        }
        SkuView sku = loadByBusinessIds(List.of(skuBusinessId)).get(skuBusinessId);
        return sku == null ? null : sku.skuTechnicalId();
    }

    /** 购物车行持有的技术键集合（A 案：{@code cart.sku_id} 保持技术键）。 */
    private static List<Long> technicalIdsOf(List<CartItem> items) {
        return items.stream()
                .map(CartItem::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 按技术键批量回填（<b>fail-open</b>：数据源异常 → 空 Map + WARN，列表照常返回）。
     *
     * <p>展示字段与业务键取自同一次查询 —— 不存在 N+1，也不存在「为拿业务键再打一次库」。
     */
    private Map<Long, SkuView> loadByTechnicalIds(List<Long> skuTechnicalIds) {
        try {
            Map<Long, SkuView> views = cartSkuViewQuery.findBySkuIds(skuTechnicalIds);
            return views == null ? Map.of() : views;
        } catch (Exception e) {
            log.warn("购物车 SKU 视图回填失败，展示字段与业务键置空（fail-open）: skuIds={}, cause={}",
                    skuTechnicalIds, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 按 <b>SKU 业务键</b>批量解析（写路径：入参收业务键 → 技术键）。
     *
     * <p>异常语义为<b>空 Map</b>：加购侧据此抛 {@code SKU_NOT_FOUND}（fail-closed），
     * 改数量/删除/改选侧据此幂等空操作 —— 由调用方决定，读模型自身不抛。
     */
    private Map<String, SkuView> loadByBusinessIds(List<String> skuBusinessIds) {
        if (skuBusinessIds == null || skuBusinessIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = skuBusinessIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, SkuView> views = cartSkuViewQuery.findBySkuBusinessIds(ids);
            return views == null ? Map.of() : views;
        } catch (Exception e) {
            log.warn("SKU 业务键解析失败（按未命中处理）: skuIds={}, cause={}", ids, e.getMessage());
            return Map.of();
        }
    }

    /**
     * 用 SKU 视图回填购物车项（展示字段 + 小计 + <b>SKU/SPU 业务键</b>）。
     *
     * <p>取不到 SKU 时<b>不回落技术键</b>（ADR-061 §决策 3）。
     */
    private void enrich(CartItemVO item, SkuView sku) {
        if (sku == null) {
            return;
        }
        item.setSkuId(sku.skuBusinessId());
        item.setSpuId(sku.spuBusinessId());
        item.setSpuName(sku.spuName());
        item.setSpec(sku.spec());
        item.setImage(sku.image());
        item.setPrice(sku.price());
        item.setSubTotal(sku.price() == null || item.getQuantity() == null
                ? 0 : sku.price() * item.getQuantity());
    }
}
