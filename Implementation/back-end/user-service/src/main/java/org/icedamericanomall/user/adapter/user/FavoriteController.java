package org.icedamericanomall.user.adapter.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.vo.FavoriteVO;
import org.icedamericanomall.user.service.FavoriteService;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public Result<?> add(@RequestParam Long productId) {
        favoriteService.add(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @DeleteMapping
    public Result<?> remove(@RequestParam Long productId) {
        favoriteService.remove(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @GetMapping
    public Result<IPage<FavoriteVO>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(favoriteService.pageByUser(UserContext.getUserId(), page, size));
    }
}
