package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.dto.AfterSaleApplyReq;
import org.icedamericanomall.domain.entity.AfterSaleEntity;

public interface AfterSaleService extends IService<AfterSaleEntity> {

    boolean existsByOrderAndUser(String orderNo, Long userId);

    IPage<AfterSaleEntity> pageByUserId(Long userId, int page, int size);

    IPage<AfterSaleEntity> pageByStatus(Integer status, int page, int size);

    /** 用户提交售后申请（含重复校验）。 */
    AfterSaleEntity applyAfterSale(Long userId, AfterSaleApplyReq req);

    /** 管理员审核售后申请。 */
    void review(Long id, Integer status, String adminRemark);
}
