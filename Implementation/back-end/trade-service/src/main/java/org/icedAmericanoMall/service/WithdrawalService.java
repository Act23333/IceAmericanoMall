package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.WithdrawalApplyReq;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;

public interface WithdrawalService extends IService<WithdrawalEntity> {

    IPage<WithdrawalEntity> pageBySeller(Long sellerId, int page, int size);

    IPage<WithdrawalEntity> pageByStatus(Integer status, int page, int size);

    /** 商家申请提现。 */
    WithdrawalEntity applyWithdrawal(Long sellerId, WithdrawalApplyReq req);

    /** 管理员审核提现；打款成功时级联更新结算单为已打款。 */
    void review(Long id, Integer status, String adminRemark);
}
