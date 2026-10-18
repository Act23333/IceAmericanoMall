package org.icedamericanomall.trade.adapter.admin;

import org.icedamericanomall.common.web.QueryModel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.trade.adapter.request.AfterSalePageReq;
import org.icedamericanomall.trade.adapter.response.AfterSaleVO;
import org.icedamericanomall.trade.app.aftersale.AfterSaleManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 — 售后审核 + 退货确认。
 *
 * <p><b>退款触发时机（F1，V5.6 纠正，ADR-037 修订 ADR-035）</b>：
 * <ul>
 *   <li><b>仅退款（type=2）</b>：审核同意（APPROVED）后即触发退款 —— 无需退货；</li>
 *   <li><b>退货退款（type=1）</b>：审核同意只落 APPROVED，<b>不</b>触发退款；
 *       待商家/管理员<b>确认收到退货</b>（{@code PUT /{id}/confirm-return}，达成 RETURNED(4)）
 *       后才触发退款 —— 货未退回不应放款。</li>
 * </ul>
 *
 * <p><b>事务纪律</b>：审核/确认退货落库（本地）<b>之后</b>，由 {@link AfterSaleManager}
 * 在事务外触发退款（I40）——Feign 严禁进事务（硬约束），故「状态落库」与「触发退款」两层分离：
 * 落库失败不回滚退款，退款失败也不回滚落库（售后单停留 APPROVED/RETURNED，可由管理端重试收敛）。
 *
 * <p><b>审计（ADR-032 管道一）</b>：两个写操作均标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}，经 ELK 采集，不落库）。
 */
@RestController
@RequestMapping("/api/admin/after-sale")
@RequiredArgsConstructor
public class AdminAfterSaleController {

    /** 审计模块：交易域售后。 */
    private static final String MODULE_TRADE_AFTER_SALE = "TRADE_AFTER_SALE";

    /** 审计动作：售后审核。 */
    private static final String ACTION_REVIEW_AFTER_SALE = "REVIEW_AFTER_SALE";

    /** 审计动作：确认收到退货。 */
    private static final String ACTION_CONFIRM_RETURN = "CONFIRM_RETURN";

    private final AfterSaleManager afterSaleManager;

    /** 售后申请分页（query: status / page / size，属性绑定）。 */
    // 5a 批 4：本控制器**必须按方法挂位**，不能类级 ——
    //   查询看列表不该被要求有退款权，而两个写方法**同步触发退款**（RefundManager#triggerRefund
    //   → refundClient.refund，Feign fail-closed），属资金动作 ⇒ 挂 pay:admin 而非 aftersale:write。
    @PreAuthorize("@ss.hasPermi('aftersale:read')")
    @GetMapping
    public Result<IPage<AfterSaleVO>> list(@QueryModel AfterSalePageReq req) {
        return Result.ok(afterSaleManager.pageByStatus(req.getStatus(), req.getPage(), req.getSize()));
    }

    /**
     * 售后审核；审核结果为「同意」<b>且为仅退款（type=2）</b>时，落库后触发退款（I40）。
     *
     * @param id     售后单 ID
     * @param status 目标状态（2-同意时按类型决定是否触发退款）
     * @param remark 审核备注
     */
    @OperationLog(module = MODULE_TRADE_AFTER_SALE, action = ACTION_REVIEW_AFTER_SALE,
            description = "售后审核", recordParams = true)
    // 资金动作：type=2（仅退款）在审核同意时**同步**发起退款 ⇒ 挂 pay:admin（Q2 资金面独立）。
    @PreAuthorize("@ss.hasPermi('pay:admin')")
    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @RequestParam Integer status,
                               @RequestParam(required = false) String remark) {
        afterSaleManager.review(id, status, remark);
        return Result.ok();
    }

    /**
     * 确认收到退货（V5.6 新增，退货退款的退款触发点）—— 仅退货退款（type=1）适用。
     *
     * @param id    售后单 ID
     * @param remark 备注（可选）
     */
    @OperationLog(module = MODULE_TRADE_AFTER_SALE, action = ACTION_CONFIRM_RETURN,
            description = "确认收到退货", recordParams = true)
    // 资金动作：type=1（退货退款）在确认收到退货时**同步**发起退款 ⇒ 挂 pay:admin。
    @PreAuthorize("@ss.hasPermi('pay:admin')")
    @PutMapping("/{id}/confirm-return")
    public Result<Void> confirmReturn(@PathVariable Long id,
                                     @RequestParam(required = false) String remark) {
        afterSaleManager.confirmReturn(id);
        return Result.ok();
    }
}
