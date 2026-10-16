package org.icedamericanomall.pay.adapter.admin;

import org.icedamericanomall.common.web.QueryModel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.pay.adapter.request.ReconciliationQueryReq;
import org.icedamericanomall.pay.adapter.response.ReconciliationBatchVO;
import org.icedamericanomall.pay.adapter.response.ReconciliationDetailVO;
import org.icedamericanomall.pay.adapter.response.RefundVO;
import org.icedamericanomall.pay.app.ReconciliationManager;
import org.icedamericanomall.pay.app.RefundManager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 — 支付域（F1 退款重试 + F4 渠道对账）。
 *
 * <p>控制器只做参数接收 + 调 Manager + 包 {@code Result}；出参装配（Domain → VO）
 * 由 Manager 内聚完成，本类不持有转换器。
 *
 * <p><b>网关前置条件</b>：{@code /api/admin/pay/**} 必须由专属路由直指 pay-service，
 * 且<b>先于</b>兜底路由 {@code /api/admin/**} 声明（后者指向 user-service），见 gateway
 * {@code application.yml}。
 *
 * <p><b>审计（ADR-032 管道一）</b>：AD43（退款重试）与 AD46（手动触发对账）为写操作，
 * 标注 {@code @OperationLog} 产出单行 JSON 审计日志（logger {@code AUDIT}，经 ELK 采集，不落库）；
 * 入参为标识/日期，故开启参数摘要。AD44/AD45 为只读查询，不产生审计留痕。
 *
 * <p><b>管理端鉴权</b>：本服务未引入 {@code spring-boot-starter-security}
 * （ia-common 中该依赖为 {@code <optional>true</optional>}，不传递），故 {@code @PreAuthorize}
 * 在此不可用；改用 {@link UserContext#hasPermission} 显式判断，语义等价于
 * {@code @PreAuthorize("@ss.hasPermi('user:admin')")}（同 merchant-service
 * {@code AdminMerchantController.assertAdmin()} 既有范式）。
 *
 * <pre>
 * AD43 POST /refund/{refundNo}/retry                       退款重试（仅失败单）
 * AD44 GET  /reconciliation/batches                        批次分页
 * AD45 GET  /reconciliation/batches/{id}/details           批次差异明细分页
 * AD46 POST /reconciliation/run?billDate=&channel=         手动触发对账（同日幂等）
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/pay")
@RequiredArgsConstructor
public class AdminPayController {

    /** 审计模块：支付域（退款 + 对账）。 */
    private static final String MODULE_PAY = "PAY_FUND";

    /** 审计动作：退款重试。 */
    private static final String ACTION_RETRY_REFUND = "RETRY_REFUND";

    /** 审计动作：手动触发对账。 */
    private static final String ACTION_RUN_RECONCILIATION = "RUN_RECONCILIATION";

    private final RefundManager refundManager;
    private final ReconciliationManager reconciliationManager;

    /**
     * AD43 退款重试 —— 仅 {@code status=失败(2)} 的退款单可重试（重新发起渠道退款，状态回处理中）。
     *
     * @param refundNo 退款单号
     * @return 重试后的退款单视图
     */
    @OperationLog(module = MODULE_PAY, action = ACTION_RETRY_REFUND,
            description = "退款重试", recordParams = true)
    @PostMapping("/refund/{refundNo}/retry")
    public Result<RefundVO> retryRefund(@PathVariable String refundNo) {
        assertAdmin();
        return Result.ok(refundManager.retry(refundNo));
    }

    /**
     * AD44 对账批次分页（按账单日倒序）。
     *
     * @param req 查询参数（page / size / channel / status / billDate[可空]）
     */
    @GetMapping("/reconciliation/batches")
    public Result<IPage<ReconciliationBatchVO>> listBatches(@QueryModel ReconciliationQueryReq req) {
        assertAdmin();
        return Result.ok(reconciliationManager
                .pageBatches(req.getPage(), req.getSize(), req.getChannel(), req.getStatus(),
                        req.parseBillDate(false)));
    }

    /**
     * AD45 批次差异明细分页（可按键 {@code result} 过滤四态）。
     *
     * @param id  批次 ID
     * @param req 查询参数（page / size / result[可空]）
     */
    @GetMapping("/reconciliation/batches/{id}/details")
    public Result<IPage<ReconciliationDetailVO>> listBatchDetails(@PathVariable Long id,
                                                                  @QueryModel ReconciliationQueryReq req) {
        assertAdmin();
        return Result.ok(reconciliationManager
                .pageDetails(id, req.getPage(), req.getSize(), req.getResult()));
    }

    /**
     * AD46 手动触发对账（{@code channel} 缺省 = 当日全部渠道；同日同渠道先删后写幂等）。
     *
     * <p>返回本次生成的批次列表 —— 契约 §4.13.14 声明的响应为单个
     * {@code ReconciliationBatchVO}，而「channel 缺省 = 全部渠道」一次会产出多条，
     * 故此处返回列表（子集情形下长度 1），已在变更交接中回报待文档同步。
     *
     * @param req 查询参数（{@code billDate} 必填 / {@code channel} 可空）
     */
    @OperationLog(module = MODULE_PAY, action = ACTION_RUN_RECONCILIATION,
            description = "手动触发渠道对账", recordParams = true)
    @PostMapping("/reconciliation/run")
    public Result<List<ReconciliationBatchVO>> runReconciliation(@QueryModel ReconciliationQueryReq req) {
        assertAdmin();
        return Result.ok(reconciliationManager.run(req.parseBillDate(true), req.getChannel()));
    }

    /** 管理员权限守卫（网关透传权限；非管理员一律拒绝）。 */
    private void assertAdmin() {
        if (!UserContext.hasPermission("user:admin")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "仅管理员可操作支付对账与退款重试");
        }
    }
}
