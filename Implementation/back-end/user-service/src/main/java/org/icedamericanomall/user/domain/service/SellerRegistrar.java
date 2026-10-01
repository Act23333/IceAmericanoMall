package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.repository.SellerRepository;

/**
 * 商家入驻领域服务 —— 跨聚合实例不变量："一个用户至多一个商家"。
 *
 * <p>纯业务逻辑：不依赖任何技术组件；创建后的状态为审核中（PENDING），
 * 审核 / 冻结等状态迁移由 {@link Seller} 实体方法承担。
 */
public class SellerRegistrar {

    private final SellerRepository sellerRepository;

    public SellerRegistrar(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    /**
     * 提交入驻申请（唯一性不变量在此保证：已有记录直接拒绝，重复提交不产生第二条）。
     *
     * @param seller 入驻申请聚合（{@code userId} 必填、状态 PENDING；经营信息由应用层组装）
     * @return 落库的商家聚合（含技术主键）
     * @throws BizException 该用户已有商家记录
     */
    public Seller register(Seller seller) {
        if (sellerRepository.findByUserId(seller.getUserId()).isPresent()) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家");
        }
        return sellerRepository.insert(seller);
    }
}
