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
import org.icedamericanomall.common.constant.CsvIdLimits;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
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
 *   <li><b>加购（写）fail-closed</b>：<b>SKU 不存在</b> → {@code SKU_NOT_FOUND}（3016），
 *       绝不静默插入一条无法下单的行。</li>
 *   <li><b>改数量/删除/改选</b>：<b>SKU 不存在</b>即<b>幂等空操作</b>（目标行本就不存在，
 *       与「删除不存在的购物车项」同语义，不产生 500）。</li>
 * </ul>
 *
 * <p><b>「形态非法」不是「不存在」（批次 ③「拆双义错误码」，2026-10-18）</b>：
 * 本类此前把「业务键拼错（长度/字符集错）」与「SKU 不存在」写成<b>同一个</b>
 * {@code SKU_NOT_FOUND(3016)}，现按 {@code collaboration/09-constraints.md} §三·六
 * 拆开、<b>不得合流</b>：
 * <table border="1">
 *   <caption>两种语义 → 两种承载</caption>
 *   <tr><th>语义</th><th>抛出</th><th>HTTP</th><th>判据（09 §三·六）</th></tr>
 *   <tr>
 *     <td><b>业务键形态非法</b>（拼错 / 长度错 / 非 hex）</td>
 *     <td>{@link ErrorCode#PARAM_ERROR}（1101）</td>
 *     <td><b>400</b></td>
 *     <td><b>参数/形态非法</b>（{@code CLIENT}）：请求本身不合法，与「查不到」无关</td>
 *   </tr>
 *   <tr>
 *     <td><b>SKU 不存在</b>（形态合法的业务键解析不到）</td>
 *     <td>{@link ErrorCode#SKU_NOT_FOUND}（3016）</td>
 *     <td><b>404</b>（批次 ① 已归位；业务码仍是 3016，不与 1103 合并）</td>
 *     <td><b>资源不存在</b></td>
 *   </tr>
 * </table>
 * <p><b>形态校验的主责在适配层</b>（{@code CartController} 经
 * {@code IdGenerator#requireWire} fail-fast，ADR-062 §决策 7「编解码唯一出口 + 形态非法 400」），
 * 本类保留<b>同口径的第二道防线</b>：拼错一个 hex 字符绝不能静默降级成「SKU 不存在」
 * 或「幂等空操作」—— 那会把排查成本全部转嫁给调用方。{@code 3016} 的 {@code HttpStatus}
 * 已由批次 ① 归位为 404（先拆语义、再改状态码，两步分别执行）。
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
     *   When 传形态合法（32-hex）但库里没有的业务键
     *   Then 抛 SKU_NOT_FOUND（fail-closed，绝不静默插入无法下单的行）
     *
     * Scenario: SKU 业务键形态非法（拼错 / 长度错 / 非 hex）
     *   When 传 "prod-001" 这类非法形态
     *   Then 抛 PARAM_ERROR（1101）+ HTTP 400 —— 属「参数非法」，不是「SKU 不存在」
     *     （适配层 CartController 已 fail-fast；此处为第二道防线）
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
        // 条目数闸门（批次 B7b）：只在「要新增一行」时校验 —— 给已有项加数量不增加条目数
        requireCartCapacity(userId);
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
     * 购物车条目数闸门：{@code count} 达到 {@link CsvIdLimits#MAX_CART_ITEMS} 即拒绝新增。
     *
     * <p><b>承载口径（ADR-063 §决策 7 表 #6，批次 B7b）</b>：这个 200 不是性能上限，是
     * <b>下单请求体预算</b> —— 业务键化后 200 项 × 32-hex ≈ 6.4 KB（技术键时代 ≈ 1.2 KB），
     * 网关 / Feign / Sentinel 的 body 预算按业务键形态重算。判据是「请求体」，<b>不得</b>
     * 借用列宽（15）或 URL（4）的判据。
     *
     * <p><b>失败语义：业务规则拒绝（200 + body code），不是 4xx</b>：资源存在、请求合法，
     * 只是规则不允许再加一条 —— 按 {@code 09-constraints.md} §三·六 属「业务规则拒绝」，
     * 故抛 {@code CART_ITEM_LIMIT_EXCEEDED(3042)} 走 200 信封，与「资源不存在」的 404 区分开。
     *
     * @param userId 用户 ID
     * @throws BizException 条目数已达上限（{@code CART_ITEM_LIMIT_EXCEEDED}）
     */
    private void requireCartCapacity(Long userId) {
        long current = cartRepository.countByUserId(userId);
        if (current >= CsvIdLimits.MAX_CART_ITEMS) {
            log.info("购物车条目数已达上限，拒绝新增: userId={}, current={}, max={}",
                    userId, current, CsvIdLimits.MAX_CART_ITEMS);
            throw new BizException(ErrorCode.CART_ITEM_LIMIT_EXCEEDED,
                    "购物车最多 " + CsvIdLimits.MAX_CART_ITEMS + " 项，请先清理再加购");
        }
    }

    /**
     * <pre>
     * Scenario: 修改购物车商品数量
     *   When PUT /api/cart/item with {sku_id(业务键), quantity}
     *   Then 更新该购物车项的数量
     * Scenario: 数量 ≤ 0 则删除该项
     * Scenario: SKU 不存在（形态合法的业务键）→ 幂等空操作（行本就不存在）
     * Scenario: SKU 业务键形态非法 → PARAM_ERROR（1101）+ HTTP 400（属参数错误，不是空操作）
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

    /** 移除购物车项（入参为 SKU 业务键；不存在 → 幂等空操作；形态非法 → 400 PARAM_ERROR）。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, String skuId) {
        Long skuTechnicalId = resolveTechnicalIdOrNull(skuId);
        if (skuTechnicalId == null) {
            return;
        }
        cartRepository.deleteByUserIdAndSkuId(userId, skuTechnicalId);
    }

    /** 切换选中状态（入参为 SKU 业务键；不存在 → 幂等空操作；形态非法 → 400 PARAM_ERROR）。 */
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
     * SKU 业务键 → 技术键（<b>加购写路径</b>，fail-closed）。
     *
     * <p><b>两种失败语义必须分开（批次 ③）</b>：
     * <ol>
     *   <li><b>形态非法</b>（长度 / 字符集错）→ {@link BadRequestException} +
     *       {@code PARAM_ERROR(1101)}（<b>HTTP 400</b>）：这是<b>参数错误</b>
     *       （{@code CLIENT}），请求本身不合法；</li>
     *   <li><b>形态合法但解析不到</b> → {@code SKU_NOT_FOUND(3016)}：这才是「资源不存在」。</li>
     * </ol>
     * 归到 3016 会把「客户端拼错一个 hex 字符」变成「SKU 不存在」，排查成本全部转嫁给调用方
     * （ADR-062 §决策 7）。
     *
     * @param skuBusinessId SKU 业务键（适配层已归一化；此处仍做形态校验，第二道防线）
     * @return SKU 技术键（{@code sku.id}）
     * @throws BadRequestException 业务键形态非法（400 / PARAM_ERROR）
     * @throws BizException        SKU 不存在（SKU_NOT_FOUND）
     */
    private Long resolveTechnicalId(String skuBusinessId) {
        requireWireShape(skuBusinessId);
        SkuView sku = loadByBusinessIds(List.of(skuBusinessId)).get(skuBusinessId);
        if (sku == null || sku.skuTechnicalId() == null) {
            log.warn("SKU 业务键解析不到（SKU 不存在, fail-closed）: skuId={}", skuBusinessId);
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku.skuTechnicalId();
    }

    /**
     * SKU 业务键 → 技术键（<b>改数量/删除/改选</b>；解析不到 → {@code null}，调用方按幂等空操作处理）。
     *
     * <p><b>形态非法仍然抛 400</b>（批次 ③）：幂等空操作针对的是「这个 SKU 眼下不在购物车里」，
     * 而<b>不是</b>「入参本身写错了」。把拼错的业务键静默当成功，等于让客户端以为已经删掉/
     * 改好了（ADR-062 §决策 7：非法形态必须是 400，不是「静默落 null」）。
     *
     * @param skuBusinessId SKU 业务键；{@code null} 表示未提供（调用方按空操作处理）
     * @return SKU 技术键，或 {@code null} 表示解析不到
     * @throws BadRequestException 业务键形态非法（400 / PARAM_ERROR）
     */
    private Long resolveTechnicalIdOrNull(String skuBusinessId) {
        if (skuBusinessId == null) {
            return null;
        }
        requireWireShape(skuBusinessId);
        SkuView sku = loadByBusinessIds(List.of(skuBusinessId)).get(skuBusinessId);
        return sku == null ? null : sku.skuTechnicalId();
    }

    /**
     * 业务键<b>形态</b>校验（第二道防线：适配层 {@code CartController} 已用
     * {@code IdGenerator.requireWire} fail-fast 过一遍）。
     *
     * <p>放在应用层不是为了替代适配层，而是为了不让「内部调用方绕过 Controller」时
     * 把非法形态降级成 not-found / 空操作 —— 守卫 {@code check-id-invariants.sh} 检查 6 只约束
     * 「{@code IdGenerator.requireWire} 可以出现在 adapter、{@code toBytes} 系列不可以」，
     * 应用层做形态判定是允许的。
     *
     * @param skuBusinessId 待校验业务键
     * @throws BadRequestException 形态非法（HTTP 400 / {@code PARAM_ERROR}）
     */
    private static void requireWireShape(String skuBusinessId) {
        if (!IdGenerator.isWire(skuBusinessId)) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    "sku_id 必须为 32 位 hex 业务键：形态非法属参数错误，不是「SKU 不存在」");
        }
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
     * <p><b>本方法只表达一件事：解析结果为「命中 / 未命中」</b>（未命中返回空 Map，自身不抛）。
     * 「未命中之后怎么办」由调用方按各端点语义决定，<b>而「形态非法」根本不走这条分支</b> ——
     * 它在进入本方法之前就被 {@link #requireWireShape(String)} 以 400
     * {@code PARAM_ERROR} 拦下（批次 ③ 拆双义：形态非法 ≠ 不存在）：
     * <ul>
     *   <li><b>加购</b>：未命中 → {@code SKU_NOT_FOUND(3016)}（fail-closed，不插脏行）；</li>
     *   <li><b>改数量/删除/改选</b>：未命中 → 幂等空操作（目标行本就不存在）。</li>
     * </ul>
     * 数据源异常同样归一为「未命中」（fail-open 读模型，ADR-057），WARN 后不抛。
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
