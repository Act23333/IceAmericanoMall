package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.SellerApplicationApplyReq;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;

import java.util.List;

public interface ApplicationService extends IService<SellerApplicationEntity> {

    boolean hasPendingApplication(Long userId);

    SellerApplicationEntity getLatestApplication(Long userId);

    /** 用户提交入驻申请（含审核中校验）。 */
    SellerApplicationEntity applyForSeller(Long userId, SellerApplicationApplyReq req);

    /** 管理员：按状态查询申请列表。 */
    List<SellerApplicationEntity> listByStatus(Integer status);

    /** 管理员审核入驻申请。 */
    void review(Long id, Integer status, String adminRemark);
}
