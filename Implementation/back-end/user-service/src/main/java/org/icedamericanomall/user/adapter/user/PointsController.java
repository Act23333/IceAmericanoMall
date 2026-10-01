package org.icedamericanomall.user.adapter.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.PointsLogVO;
import org.icedamericanomall.user.app.points.PointsManager;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户积分接口（适配层）—— 余额查询 + 明细分页。
 */
@RestController
@RequestMapping("/api/user/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsManager pointsManager;

    @GetMapping("/balance")
    public Result<Map<String, Object>> getBalance() {
        Long userId = UserContext.getUserId();
        assert userId != null;
        return Result.ok(Map.of(
                "userId", userId,
                "balance", pointsManager.getBalance(userId)));
    }

    @GetMapping("/history")
    public Result<IPage<PointsLogVO>> getHistory(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        return Result.ok(pointsManager.getHistory(userId, page, size));
    }
}
