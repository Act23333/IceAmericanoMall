package org.icedamericanomall.merchant.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;

/**
 * 商家入驻领域服务 —— 跨聚合不变量："一个用户至多一个商家"，
 * 且申请成功时"商家身份 + 默认店铺"成对创建（ADR-019）。
 *
 * <p>纯业务逻辑：不依赖任何技术组件（事务边界在应用层
 * {@code MerchantManager#register}）。唯一性由
 * "预检查 + {@code uk_user_id} 唯一键"双层保证：预检查覆盖常规重复提交，
 * 唯一键覆盖并发竞态（持久化层把冲突翻译为 {@link BizException}）。
 */
public class MerchantRegistrar {

    private final MerchantRepository merchantRepository;
    private final ShopRepository shopRepository;

    public MerchantRegistrar(MerchantRepository merchantRepository, ShopRepository shopRepository) {
        this.merchantRepository = merchantRepository;
        this.shopRepository = shopRepository;
    }

    /**
     * 提交入驻申请：创建商家身份（PENDING）+ 默认店铺（PENDING）。
     *
     * @param merchant    入驻申请聚合（{@code userId} 必填、状态 PENDING）
     * @param defaultShop 默认店铺聚合（资料来自申请参数；归属由本方法回填）
     * @return 落库的商家聚合（含技术主键）
     * @throws BizException 该用户已有商家记录（USER_ALREADY_EXISTS）
     */
    public Merchant register(Merchant merchant, Shop defaultShop) {
        if (merchantRepository.findByUserId(merchant.getUserId()).isPresent()) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家");
        }
        Merchant saved = merchantRepository.insert(merchant);
        defaultShop.assignMerchant(saved.getId());
        shopRepository.insert(defaultShop);
        return saved;
    }
}
