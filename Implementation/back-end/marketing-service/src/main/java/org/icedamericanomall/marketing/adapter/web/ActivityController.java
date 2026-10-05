package org.icedamericanomall.marketing.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.marketing.adapter.response.ActivityVO;
import org.icedamericanomall.marketing.app.promotion.ActivityManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 营销活动中心接口（前台/公开入口，V5.11 M12/M13）。
 *
 * <pre>
 * GET /api/activity        活动中心列表（🌐 匿名；可选 type 过滤）
 * GET /api/activity/{id}   活动详情（🌐 匿名；进行中/未开始/已结束均可查）
 * </pre>
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal），本类为前台/公开入口。
 * 控制器只做参数绑定与委托，出参 {@code Result<ActivityVO>}（聚合不泄漏到适配层之外）。
 */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityManager activityManager;

    /** 活动中心列表：全店铺进行中活动；type 缺省=全部（NEW_USER 契约定稿为空列表）。 */
    @GetMapping
    public Result<List<ActivityVO>> list(@RequestParam(required = false) String type) {
        return Result.ok(activityManager.listActiveActivities(type));
    }

    /** 活动详情；不存在 → NO_RESOURCE_FOUND(1103)。 */
    @GetMapping("/{id}")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        return Result.ok(activityManager.getActivityDetail(id));
    }
}
