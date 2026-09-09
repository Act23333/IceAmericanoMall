package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.domain.dto.SellerRegisterReq;
import org.icedamericanomall.domain.dto.UpdateShopReq;
import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.domain.vo.SellerVO;

public interface SellerService extends IService<SellerEntity> {

    /** 内部使用：按用户ID获取商家实体（不对外暴露）。 */
    SellerEntity getByUserId(Long userId);

    /** 商家申请入驻。 */
    void register(Long userId, SellerRegisterReq req);

    /** 审核/变更商家状态。 */
    void updateStatus(Long id, Integer status);

    /** 获取当前用户店铺信息（VO），未开通则抛异常。 */
    SellerVO getShopVO(Long userId);

    /** null-safe 部分更新店铺信息。 */
    void updateShop(Long userId, UpdateShopReq req);

    /** 管理后台：分页查询待审核商家。 */
    IPage<SellerVO> pagePending(int page, int size);
}
