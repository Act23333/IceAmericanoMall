package org.icedamericanomall.user.app.seller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.adapter.request.SellerRegisterReq;
import org.icedamericanomall.user.adapter.request.UpdateShopReq;
import org.icedamericanomall.user.adapter.response.SellerVO;
import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.model.SellerStatusEnum;
import org.icedamericanomall.user.domain.repository.SellerRepository;
import org.icedamericanomall.user.domain.service.SellerRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商家管理（应用层编排）—— 入驻申请、店铺信息、管理后台审核。
 *
 * <p>"一个用户至多一个商家"的不变量在领域服务 {@link SellerRegistrar} 中保证。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SellerManager {

    private final SellerRepository sellerRepository;
    private final SellerRegistrar sellerRegistrar;

    /** 商家申请入驻（经营信息组装后交由领域服务保证唯一性不变量）。 */
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, SellerRegisterReq req) {
        Seller seller = Seller.apply(userId);
        seller.setShopName(req.getShopName());
        seller.setContactPhone(req.getContactPhone());
        seller.setProvince(req.getProvince());
        seller.setCity(req.getCity());
        seller.setDistrict(req.getDistrict());
        seller.setDetailAddress(req.getDetailAddress());
        sellerRegistrar.register(seller);
    }

    /** 审核/变更商家状态（管理后台；状态值由领域枚举校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        sellerRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "商家不存在"));
        sellerRepository.updateStatusById(id, SellerStatusEnum.fromCode(status));
    }

    /** 获取当前用户店铺信息（VO），未开通则抛异常。 */
    public SellerVO getShopVO(Long userId) {
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家"));
        return SellerVO.from(seller);
    }

    /** null-safe 部分更新店铺信息（空白字符串视为未提供，语义与迁移前一致）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Long userId, UpdateShopReq req) {
        Seller seller = sellerRepository.findByUserId(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "您尚未开通商家"));

        seller.updateShopInfo(
                blankToNull(req.getShopName()),
                blankToNull(req.getShopLogo()),
                blankToNull(req.getContactPhone()),
                req.getProvince(),
                req.getCity(),
                req.getDistrict(),
                req.getDetailAddress());
        sellerRepository.updateShopInfo(seller);
    }

    /** 管理后台：分页查询待审核商家。 */
    public IPage<SellerVO> pagePending(int pageNum, int size) {
        long total = sellerRepository.countByStatus(SellerStatusEnum.PENDING);
        Page<SellerVO> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<Seller> sellers = sellerRepository.findPageByStatus(
                SellerStatusEnum.PENDING, (pageNum - 1) * size, size);
        page.setRecords(sellers.stream().map(SellerVO::from).toList());
        page.setPages((long) Math.ceil((double) total / size));
        return page;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
