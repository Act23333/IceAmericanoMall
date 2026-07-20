package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.FavoriteVO;
import org.icedAmericanoMall.service.FavoriteService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public Result<?> add(@RequestParam Long productId) {
        favoriteService.add(UserContext.getUser().userId(), productId);
        return Result.ok();
    }

    @DeleteMapping
    public Result<?> remove(@RequestParam Long productId) {
        favoriteService.remove(UserContext.getUser().userId(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<IPage<FavoriteVO>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(favoriteService.pageByUser(UserContext.getUser().userId(), page, size));
    }
}
