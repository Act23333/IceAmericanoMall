package org.icedamericanomall.user.adapter.web.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.user.app.member.MemberLevelManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员域内部接口（F6 会员等级与权益，契约 §4.14.1b I45-I46，V5.8）。
 *
 * <p>调用方 trade-service：I45 定价中心取会员折扣率（下单计价）、I46 订单完成成长值入账
 * （确认收货后）。降级语义 fail-open（ADR-044）：调用方熔断降级为默认 NORMAL
 * （ia-api {@code MemberClientFallback}）；本端点保证查询/入账语义正确 ——
 * I46 幂等锚点 orderNo，重复请求返回当前等级权益、不报错。
 *
 * <p>内部端点返回裸契约对象（不包 {@code Result}）；业务异常由 ia-common
 * {@code GlobalExceptionHandler} 按真实 HTTP 状态 + 最小 JSON 透出（ADR-004）。
 */
@RestController
@RequestMapping("/internal/user/member")
@RequiredArgsConstructor
@Validated
public class InternalMemberController {

    private final MemberLevelManager memberLevelManager;

    /** I45 查询会员等级与权益（会员折扣率万分比 + 积分倍率百分比）。 */
    @GetMapping("/level")
    public MemberLevelCO getLevel(@RequestParam("userId") Long userId) {
        return memberLevelManager.getLevel(userId);
    }

    /** I46 订单完成成长值入账（幂等：orderNo 唯一，重复请求返回当前等级权益）。 */
    @PostMapping("/growth")
    public MemberLevelCO recordGrowth(@Validated @RequestBody MemberGrowthCO growth) {
        return memberLevelManager.recordGrowth(growth);
    }
}
