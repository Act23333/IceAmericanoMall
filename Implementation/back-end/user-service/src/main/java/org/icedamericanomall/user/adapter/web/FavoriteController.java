package org.icedamericanomall.user.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.app.favorite.FavoriteManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.web.bind.annotation.*;

/**
 * 商品收藏接口（适配层）—— 只做参数校验 + 委托 Manager + Result 包装；
 * 用户身份一律取自登录上下文（{@link UserContext}），不信任前端传参。
 */
@RestController
@RequestMapping("/api/user/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteManager favoriteManager;

    /** 收藏商品（幂等：重复收藏不重复插入）。 */
    @PostMapping
    public Result<Void> add(@RequestParam(name = "spu_id") Long spuId) {
        favoriteManager.add(UserContext.getUserId(), spuId);
        return Result.ok();
    }

    /** 取消收藏。 */
    @DeleteMapping
    public Result<Void> remove(@RequestParam(name = "spu_id") Long spuId) {
        favoriteManager.remove(UserContext.getUserId(), spuId);
        return Result.ok();
    }

    /** 分页查询本人收藏（按收藏时间倒序）。 */
    @GetMapping
    public Result<IPage<FavoriteVO>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(favoriteManager.pageByUser(UserContext.getUserId(), page, size));
    }
}
