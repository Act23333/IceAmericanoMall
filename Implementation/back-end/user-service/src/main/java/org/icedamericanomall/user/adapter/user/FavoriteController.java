package org.icedamericanomall.user.adapter.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.app.favorite.FavoriteManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteManager favoriteManager;

    @PostMapping
    public Result<?> add(@RequestParam Long productId) {
        favoriteManager.add(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @DeleteMapping
    public Result<?> remove(@RequestParam Long productId) {
        favoriteManager.remove(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<IPage<FavoriteVO>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(favoriteManager.pageByUser(UserContext.getUserId(), page, size));
    }
}
