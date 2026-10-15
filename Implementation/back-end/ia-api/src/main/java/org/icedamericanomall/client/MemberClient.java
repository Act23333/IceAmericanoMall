package org.icedamericanomall.client;

import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.fallback.MemberClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会员等级与权益 Feign 客户端（F6 会员等级：trade-service → user-service，契约 §4.14.1b I45-I46，V5.8）。
 *
 * <p>调用方：trade-service 价格计算中心（会员折扣，I45）与订单完成成长值入账（I46，ADR-044）。
 *
 * <p><b>降级语义：fail-open（默认无折扣 + 告警）</b>，属 ADR-011 三分法的 <b>① 非判别型辅助查询</b>：
 * <ul>
 *   <li>I45 返回的折扣率缺失只意味着用户少得优惠（方向是体验损失，可补偿），不是平台资损；
 *       等级是准静态数据，下单可用性优先——降级返回默认 NORMAL（不折扣）并告警；与 I43/I44
 *       fail-closed 的差异及理由见 ADR-044；</li>
 *   <li>I46 成长值入账失败不阻断交易主流程（与触达接线 ADR-042 同口径），等级成长可由后续
 *       订单继续累计（Phase 1 不做补账任务）。</li>
 * </ul>
 */
@FeignClient(name = "user-service", path = "/internal/user/member",
        contextId = "member", fallbackFactory = MemberClientFallback.class)
public interface MemberClient {

    /**
     * 查询会员等级与权益（折扣率/积分倍率）。
     *
     * @param userId 买家 user_id
     * @return 等级权益；降级时为默认 NORMAL（不折扣、1 倍积分）
     */
    @GetMapping("/level")
    MemberLevelCO getLevel(@RequestParam(name = "user_id") Long userId);

    /**
     * 订单完成成长值入账（幂等：orderNo 唯一，重复请求返回当前等级，ADR-044）。
     *
     * @param growth 入账参数（userId/orderNo/payAmount）
     * @return 入账后等级权益；降级时为 {@code null}（调用方忽略返回值）
     */
    @PostMapping("/growth")
    MemberLevelCO recordGrowth(@RequestBody MemberGrowthCO growth);
}
