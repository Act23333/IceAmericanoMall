package org.icedamericanomall.cart.adapter.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.cart.app.CartManager;
import org.icedamericanomall.cart.adapter.request.CartAddReq;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.adapter.response.CartVO;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.utils.IdGenerator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 购物车端点（C1-C6）。
 *
 * <p><b>SKU 业务键的形态校验在「本层」fail-fast</b>（ADR-062 §决策 7 / 批次 ③ 拆双义错误码）：
 * C2/C3 的请求体 {@code sku_id} 与 C4/C5 的路径变量 {@code {skuId}} 一律先过
 * {@link IdGenerator#requireWire}：
 * <ul>
 *   <li><b>形态非法</b>（拼错 / 长度错 / 非 hex）→ {@code PARAM_ERROR(1101)} + <b>HTTP 400</b>，
 *       <b>不触达应用层</b>。这是「参数非法」，不是「SKU 不存在」——
 *       若留到应用层才拦，客户端拼错一个 hex 字符会得到「SKU 不存在」
 *       或（更糟）一次静默成功的空操作；</li>
 *   <li><b>形态合法</b> → 归一化为 32 位小写 hex 后交给 {@link CartManager}
 *       （大小写 / 36 位连字符 UUID 是合法形态，此处统一写法，库内只有一种形态）。</li>
 * </ul>
 * 这一步只是<b>形态</b>校验，不做存在性判断、不查库 —— 存在性由应用层按各端点语义处理
 * （C2 不存在 → 3016 fail-closed；C3/C4/C5 不存在 → 幂等空操作）。
 */
@Slf4j
@RequestMapping("/api/cart")
@RestController
@RequiredArgsConstructor
@Validated
public class CartController {

    private final CartManager cartManager;

    /** 购物车视图：商品名/规格/单价/图片由 CartManager 编排层批量回填。 */
    @GetMapping
    public Result<CartVO> getCart() {
        Long userId = UserContext.getUserId();
        return Result.ok(cartManager.getCartView(userId));
    }

    /**
     * C2 加入购物车。
     *
     * <p>形态校验先于身份读取：非法报文不必走到用户上下文（fail-fast）。
     */
    @PostMapping("/item")
    public Result<Void> addItem(@Valid @RequestBody CartAddReq req) {
        req.setSkuId(IdGenerator.requireWire("sku_id", req.getSkuId()));
        Long userId = UserContext.getUserId();
        cartManager.addItem(userId, req);
        return Result.ok();
    }

    /**
     * C3 修改购物车项数量。
     *
     * <p>语义说明：quantity 允许 ≤ 0（不校验下限），此时该购物车项被删除 —— 与 H2 集成测试
     * {@code CartServiceImplH2Test#shouldRemove_whenQuantityZero} 的口径一致。
     */
    @PutMapping("/item")
    public Result<Void> updateItem(@Valid @RequestBody CartUpdateReq req) {
        req.setSkuId(IdGenerator.requireWire("sku_id", req.getSkuId()));
        Long userId = UserContext.getUserId();
        cartManager.updateItem(userId, req);
        return Result.ok();
    }

    /**
     * 移除购物车项（C4）。
     *
     * <p>路径变量 {@code skuId} 为 <b>SKU 业务键</b> 32-hex（契约 §1.8② 明确：C4/C5/C10 的路径
     * 变量属业务键口径 —— 它们明确代表 SKU 实体，必须与 {@code CartItemVO.sku_id} 同型同义，
     * 否则前端拿到列表后无法删自己的行）。服务端负责业务键 → 技术键解析。
     */
    @DeleteMapping("/item/{skuId}")
    public Result<Void> removeItem(@PathVariable @NotBlank(message = "SKU ID不能为空") String skuId) {
        String wire = IdGenerator.requireWire("skuId", skuId);
        Long userId = UserContext.getUserId();
        cartManager.removeItem(userId, wire);
        return Result.ok();
    }

    /** 切换购物车项选中状态（C5；路径变量 {@code skuId} 同 C4：SKU 业务键 32-hex）。 */
    @PatchMapping("/item/{skuId}/selected")
    public Result<Void> selectItem(@PathVariable String skuId,
                                   @RequestParam @NotNull Boolean selected) {
        String wire = IdGenerator.requireWire("skuId", skuId);
        Long userId = UserContext.getUserId();
        cartManager.selectItem(userId, wire, selected);
        return Result.ok();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        Long userId = UserContext.getUserId();
        cartManager.clearCart(userId);
        return Result.ok();
    }
}
