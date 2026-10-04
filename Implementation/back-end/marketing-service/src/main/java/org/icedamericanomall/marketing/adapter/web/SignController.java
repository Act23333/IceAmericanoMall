package org.icedamericanomall.marketing.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.SignResultVO;
import org.icedamericanomall.marketing.app.sign.SignManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日签到控制器（适配层）—— 基于 Redis Bitmap，连续签到积分递增。
 * 承接原 user-service {@code /api/user/sign}（ADR-016 收尾，报文形状不变）。
 *
 * <p><b>归位说明</b>：适配层子包按受众分组（web/admin/internal），本类为前台/公开入口。
 *
 * <pre>
 * Scenario: 每日签到成功
 *   Given 用户已登录且今天尚未签到
 *   When POST /api/sign
 *   Then Bitmap 标记当天已签到，按连续天数发放递增积分（10/15/20）
 *   And 返回 { signed, earnedPoints, monthCount, continuousDays }
 *
 * Scenario: 重复签到幂等保护
 *   Given 今天已签到
 *   When POST /api/sign
 *   Then signed=false, earnedPoints=0，仍返回统计
 * </pre>
 */
@RestController
@RequestMapping("/api/sign")
@RequiredArgsConstructor
public class SignController {

    private final SignManager signManager;
    private final MarketingConverter marketingConverter;

    /** 每日签到（首次签到发放积分，重复签到幂等）。 */
    @PostMapping
    public Result<SignResultVO> sign() {
        return Result.ok(marketingConverter.toSignResultVO(signManager.sign(UserContext.getUserId())));
    }

    /** 签到状态查询（不发放积分）。 */
    @GetMapping("/status")
    public Result<SignResultVO> status() {
        return Result.ok(marketingConverter.toSignResultVO(signManager.status(UserContext.getUserId())));
    }
}
