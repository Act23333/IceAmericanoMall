package org.icedamericanomall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedamericanomall.domain.dto.WithdrawalApplyReq;
import org.icedamericanomall.domain.entity.WithdrawalEntity;
import org.icedamericanomall.enums.WithdrawalStatusEnum;
import org.icedamericanomall.mapper.WithdrawalMapper;
import org.icedamericanomall.service.SettlementService;
import org.icedamericanomall.service.WithdrawalService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalServiceImpl extends ServiceImpl<WithdrawalMapper, WithdrawalEntity> implements WithdrawalService {

    private final SettlementService settlementService;

    public WithdrawalServiceImpl(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Override
    public IPage<WithdrawalEntity> pageBySeller(Long sellerId, int page, int size) {
        return lambdaQuery()
                .eq(WithdrawalEntity::getSellerId, sellerId)
                .orderByDesc(WithdrawalEntity::getCreateTime)
                .page(new Page<>(page, size));
    }

    @Override
    public IPage<WithdrawalEntity> pageByStatus(Integer status, int page, int size) {
        var wrapper = lambdaQuery().orderByDesc(WithdrawalEntity::getCreateTime);
        if (status != null) wrapper.eq(WithdrawalEntity::getStatus, status);
        return wrapper.page(new Page<>(page, size));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WithdrawalEntity applyWithdrawal(Long sellerId, WithdrawalApplyReq req) {
        WithdrawalEntity entity = new WithdrawalEntity();
        entity.setSellerId(sellerId);
        entity.setWithdrawalNo(IdUtil.fastSimpleUUID());
        entity.setAmount(req.getAmount());
        entity.setBankAccount(req.getBankAccount());
        entity.setBankName(req.getBankName());
        entity.setStatus(WithdrawalStatusEnum.PENDING_REVIEW.getCode());
        save(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, Integer status, String adminRemark) {
        WithdrawalEntity w = getById(id);
        if (w == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "提现申请不存在");
        }
        w.setStatus(status);
        w.setAdminRemark(adminRemark);
        updateById(w);
        // 审核通过（已打款）→ 级联将该商家已结算单据置为已打款
        if (status != null && status.equals(WithdrawalStatusEnum.PAID_OUT.getCode())) {
            settlementService.markSellerSettlementsPaidOut(w.getSellerId());
        }
    }
}
