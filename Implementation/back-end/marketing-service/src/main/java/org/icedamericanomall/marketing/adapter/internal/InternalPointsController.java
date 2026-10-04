package org.icedamericanomall.marketing.adapter.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分域内部端点（适配层）—— ia-api {@code PointsClient} 契约（原 user-service 同路径，ADR-016 收尾）。
 *
 * <p>{@code /internal/**} 不包装 {@link org.icedamericanomall.common.api.Result}；
 * 调用方：trade-service（下单奖励）、user-service（新用户建档奖励）。
 *
 * <p><b>默认值与 ia-api 契约强绑定</b>：{@code PointsClient.addPoints} 声明
 * {@code type defaultValue=2}、{@code source defaultValue=下单奖励}，
 * 本端点默认值必须与之逐字一致（常量化以免两处漂移）。
 */
@Validated
@RestController
@RequestMapping("/internal/points")
@RequiredArgsConstructor
public class InternalPointsController {

    /** 默认积分类型：2=下单（与 ia-api PointsClient 契约一致）。 */
    private static final String DEFAULT_POINTS_TYPE = "2";

    /** 默认来源描述（与 ia-api PointsClient 契约一致）。 */
    private static final String DEFAULT_POINTS_SOURCE = "下单奖励";

    private final PointsManager pointsManager;

    /** 添加积分（签到/下单/任务），返回当前总余额。 */
    @PostMapping("/add")
    public long addPoints(@RequestParam @NotNull @Min(1) Long userId,
                          @RequestParam int points,
                          @RequestParam(defaultValue = DEFAULT_POINTS_TYPE) @Min(1) int type,
                          @RequestParam(defaultValue = DEFAULT_POINTS_SOURCE) @NotBlank String source) {
        return pointsManager.addPoints(userId, points, type, source);
    }
}
