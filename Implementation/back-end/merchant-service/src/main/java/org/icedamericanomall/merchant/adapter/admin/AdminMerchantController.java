package org.icedamericanomall.merchant.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.merchant.adapter.response.MerchantVO;
import org.icedamericanomall.merchant.app.seller.MerchantManager;
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
 * 角色守卫说明：本服务不引入 Spring Security（网关统一鉴权 + 请求头透传身份），
 * 管理员权限用 {@link UserContext#hasPermission} 显式判断
 * （语义等价于原 {@code @PreAuthorize("@ss.hasPermi('user:admin')")}}）。
 */
@RestController
@RequestMapping("/api/admin/seller")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantManager merchantManager;

    /** 待审核商家分页。 */
    @GetMapping("/pending")
    public Result<IPage<MerchantVO>> pendingSellers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        assertAdmin();
        return Result.ok(merchantManager.pagePending(page, size));
    }

    /** 审批通过（状态→正常 + 自动授予卖家角色，失败可重试）。 */
    @PutMapping("/{id}/approve")
    public Result<Void> approveSeller(@PathVariable Long id) {
        assertAdmin();
        merchantManager.approve(id);
        return Result.ok();
    }

    /** 管理员权限守卫（网关透传权限；非管理员一律拒绝）。 */
    private void assertAdmin() {
        if (!UserContext.hasPermission("user:admin")) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "仅管理员可操作商家审核");
        }
    }
}
