package org.icedamericanomall.marketing.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.marketing.adapter.response.PointsBalanceVO;
import org.icedamericanomall.marketing.adapter.response.PointsLogVO;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分公开端点（适配层）—— 承接原 user-service {@code /api/user/points/**}（ADR-016 收尾）。
 *
 * <pre>
 * GET /api/points/balance   积分总余额
 * GET /api/points/history   积分明细分页
 * </pre>
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal），本类为前台/公开入口。
 * 出参一律为强类型 VO（不再返回 {@code Map}）。
 */
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsManager pointsManager;

    /** 积分总余额。 */
    @GetMapping("/balance")
    public Result<PointsBalanceVO> getBalance() {
        return Result.ok(pointsManager.getBalance(UserContext.getUserId()));
    }

    /** 积分明细分页。 */
    @GetMapping("/history")
    public Result<IPage<PointsLogVO>> getHistory(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.ok(pointsManager.getHistory(UserContext.getUserId(), page, size));
    }
}
