package org.icedamericanomall.merchant.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.merchant.adapter.response.MerchantVO;
import org.icedamericanomall.merchant.app.seller.MerchantManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员商家审核（适配层）—— 随商家域自 user-service 迁入（ADR-016 收尾）。
 *
 * <pre>
 * GET /api/admin/seller/pending       待审核商家分页
 * PUT /api/admin/seller/{id}/approve  审批通过（状态→正常 + 经授权域授予卖家角色）
 * </pre>
 *
 * <p>角色授予经 ia-api {@code AuthContextClient} 调 authorization-service（授权归授权域）。
 *
 * <p><b>管理端鉴权（Q5 裁决，2026-10-18）</b>：本服务已引入 {@code spring-boot-starter-security}，
 * 故改为<b>声明式</b>类级 {@link PreAuthorize}（Q5 明确「不做双轨」——原 {@code assertAdmin()}
 * 代码内判断已删除，避免两套机制并存导致的漏判）。
 */
@RestController
@RequestMapping("/api/admin/seller")
// TODO(拆位, open-items §1.17): 权限位拆分后改为 @ss.hasPermi('merchant:admin')；
//   现在是过渡态：所有管理端共用一个 user:admin，资金/审核面尚未按域隔离。
@PreAuthorize("@ss.hasPermi('user:admin')")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantManager merchantManager;

    /** 待审核商家分页。 */
    @GetMapping("/pending")
    public Result<IPage<MerchantVO>> pendingSellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(merchantManager.pagePending(page, size));
    }

    /** 审批通过（状态→正常 + 自动授予卖家角色，失败可重试）。 */
    @PutMapping("/{id}/approve")
    public Result<Void> approveSeller(@PathVariable Long id) {
        merchantManager.approve(id);
        return Result.ok();
    }

}
