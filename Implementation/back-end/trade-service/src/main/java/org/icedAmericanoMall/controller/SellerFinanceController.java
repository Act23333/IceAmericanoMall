package org.icedAmericanoMall.controller;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.SettlementEntity;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;
import org.icedAmericanoMall.enums.WithdrawalStatusEnum;
import org.icedAmericanoMall.mapper.SettlementMapper;
import org.icedAmericanoMall.mapper.WithdrawalMapper;
import org.icedAmericanoMall.service.SettlementService;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 商家财务中心 */
@RestController
@RequestMapping("/api/seller/finance")
@RequiredArgsConstructor
public class SellerFinanceController {

    private final SettlementService settlementService;
    private final SettlementMapper settlementMapper;
    private final WithdrawalMapper withdrawalMapper;

    /** 结算单列表 */
    @GetMapping("/settlement/page")
    public Result<IPage<SettlementEntity>> pageSettlement(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(settlementService.pageBySeller(UserContext.getUser(), page, size));
    }

    /** 结算单详情 */
    @GetMapping("/settlement/{id}")
    public Result<SettlementEntity> settlementDetail(@PathVariable Long id) {
        SettlementEntity s = settlementMapper.selectById(id);
        if (s == null || !s.getSellerId().equals(UserContext.getUser()))
            throw new BizException(ErrorCode.FORBIDDEN);
        return Result.ok(s);
    }

    /** 可提现余额 = 所有已结算未打款金额 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() {
        Long sellerId = UserContext.getUser();
        int amount = settlementMapper.selectList(
                new LambdaQueryWrapper<SettlementEntity>()
                        .eq(SettlementEntity::getSellerId, sellerId)
                        .eq(SettlementEntity::getStatus, 2))
                .stream().mapToInt(s -> s.getSettlementAmount() != null ? s.getSettlementAmount() : 0).sum();
        return Result.ok(Map.of("sellerId", sellerId, "balance", amount));
    }

    /** 申请提现 */
    @PostMapping("/withdrawal")
    public Result<WithdrawalEntity> withdraw(@RequestBody WithdrawalEntity entity) {
        Long sellerId = UserContext.getUser();
        entity.setSellerId(sellerId); entity.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        entity.setWithdrawalNo(IdUtil.fastSimpleUUID());
        withdrawalMapper.insert(entity);
        return Result.ok(entity);
    }

    /** 提现记录 */
    @GetMapping("/withdrawal/page")
    public Result<?> pageWithdrawal(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        Long sellerId = UserContext.getUser();
        return Result.ok(withdrawalMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<WithdrawalEntity>()
                        .eq(WithdrawalEntity::getSellerId, sellerId)
                        .orderByDesc(WithdrawalEntity::getCreateTime)));
    }
}
