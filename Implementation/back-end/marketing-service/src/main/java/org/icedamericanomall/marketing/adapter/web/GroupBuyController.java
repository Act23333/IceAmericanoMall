package org.icedamericanomall.marketing.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.adapter.response.GroupTeamVO;
import org.icedamericanomall.marketing.app.groupbuy.GroupBuyParticipationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 拼团 C 端接口（前台/公开入口，V5.14 M15-M17，ADR-049）。
 *
 * <pre>
 * GET /api/group-buy/activities     进行中活动分页（🌐 匿名，网关仅 GET 放行）
 * GET /api/group-buy/activity/{id}  活动详情 + 最近进行中团 ≤10（🌐 匿名）
 * GET /api/group-buy/team/{teamNo}  团进度查询（🌐 匿名）
 * </pre>
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal），本类为前台/公开入口。
 * 控制器只做参数绑定与委托。参团动作本身是 T27 下单 + 支付成功后自动入团（I53），
 * 不经本控制器。<b>隐私口径</b>：团列表/团详情不出任何 userId（VO 无该字段）。
 */
@RestController
@RequestMapping("/api/group-buy")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyParticipationManager groupBuyParticipationManager;

    /** M15：进行中拼团活动分页（时间窗推导口径）。 */
    @GetMapping("/activities")
    public Result<IPage<GroupBuyActivityVO>> activities(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.ok(groupBuyParticipationManager.pageActiveActivities(page, size));
    }

    /** M16：活动详情 + 最近进行中团 ≤10（不含他人 userId）；不存在 → NO_RESOURCE_FOUND(1103)。 */
    @GetMapping("/activity/{id}")
    public Result<GroupBuyActivityVO> activityDetail(@PathVariable Long id) {
        return Result.ok(groupBuyParticipationManager.getActivityDetail(id));
    }

    /** M17：团进度查询（teamNo 寻址）；不存在 → NO_RESOURCE_FOUND(1103)。 */
    @GetMapping("/team/{teamNo}")
    public Result<GroupTeamVO> teamDetail(@PathVariable String teamNo) {
        return Result.ok(groupBuyParticipationManager.getTeamDetail(teamNo));
    }
}
