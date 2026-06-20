package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;
import org.icedAmericanoMall.enums.SettlementStatusEnum;
import org.icedAmericanoMall.enums.WithdrawalStatusEnum;
import org.icedAmericanoMall.mapper.SettlementMapper;
import org.icedAmericanoMall.mapper.WithdrawalMapper;
import org.icedAmericanoMall.service.SettlementService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理后台 — 财务结算 */
@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final SettlementService settlementService;
    private final SettlementMapper settlementMapper;
    private final WithdrawalMapper withdrawalMapper;

    /** 手动生成结算单 */
    @PostMapping("/settlement/generate")
    public Result<?> generate(@RequestParam Long sellerId,
                               @RequestParam String periodStart,
                               @RequestParam String periodEnd) {
        return Result.ok(settlementService.generate(sellerId, periodStart, periodEnd));
    }

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<?> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var result = settlementMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<org.icedAmericanoMall.domain.entity.SettlementEntity>()
                        .orderByDesc(org.icedAmericanoMall.domain.entity.SettlementEntity::getCreateTime));
        return Result.ok(result);
    }

    /** 提现审核列表 */
    @GetMapping("/withdrawal/page")
    public Result<List<WithdrawalEntity>> pageWithdrawal(@RequestParam(defaultValue = "1") Integer status) {
        return Result.ok(withdrawalMapper.selectList(
                new LambdaQueryWrapper<WithdrawalEntity>()
                        .eq(status != null, WithdrawalEntity::getStatus, status)
                        .orderByDesc(WithdrawalEntity::getCreateTime)));
    }

    /** 审核提现 */
    @PutMapping("/withdrawal/{id}/review")
    public Result<?> reviewWithdrawal(@PathVariable Long id, @RequestParam Integer status,
                                       @RequestParam(required = false) String remark) {
        WithdrawalEntity w = withdrawalMapper.selectById(id);
        if (w == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "提现申请不存在");
        w.setStatus(status); w.setAdminRemark(remark);
        withdrawalMapper.updateById(w);
        // 审核通过 → 结算单状态更新为已打款
        if (status.equals(WithdrawalStatusEnum.PAID_OUT.getCode())) {
            settlementMapper.selectList(
                    new LambdaQueryWrapper<org.icedAmericanoMall.domain.entity.SettlementEntity>()
                            .eq(org.icedAmericanoMall.domain.entity.SettlementEntity::getSellerId, w.getSellerId())
                            .eq(org.icedAmericanoMall.domain.entity.SettlementEntity::getStatus, SettlementStatusEnum.SETTLED.getCode()))
                    .forEach(s -> { s.setStatus(SettlementStatusEnum.PAID_OUT.getCode()); settlementMapper.updateById(s); });
        }
        return Result.ok();
    }
}
