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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/item")
    public Result<Void> addItem(@Valid @RequestBody CartAddReq req) {
        Long userId = UserContext.getUserId();
        cartManager.addItem(userId, req);
        return Result.ok();
    }

    /**
     * 修改购物车项数量。
     *
     * <p>语义说明：quantity 允许 ≤ 0（不校验下限），此时该购物车项被删除 —— 与 H2 集成测试
     * {@code CartServiceImplH2Test#shouldRemove_whenQuantityZero} 的口径一致。
     */
    @PutMapping("/item")
    public Result<Void> updateItem(@Valid @RequestBody CartUpdateReq req) {
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
        Long userId = UserContext.getUserId();
        cartManager.removeItem(userId, skuId);
        return Result.ok();
    }

    /** 切换购物车项选中状态（C5；路径变量 {@code skuId} 同 C4：SKU 业务键 32-hex）。 */
    @PatchMapping("/item/{skuId}/selected")
    public Result<Void> selectItem(@PathVariable String skuId,
                                   @RequestParam @NotNull Boolean selected) {
        Long userId = UserContext.getUserId();
        cartManager.selectItem(userId, skuId, selected);
        return Result.ok();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        Long userId = UserContext.getUserId();
        cartManager.clearCart(userId);
        return Result.ok();
    }
}
