package org.icedamericanomall.merchant.app.seller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.adapter.request.MerchantApplyReq;
import org.icedamericanomall.merchant.adapter.response.MerchantVO;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.icedamericanomall.merchant.domain.service.MerchantRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商家管理（应用层编排）—— 商家身份生命周期：入驻申请、审核 / 状态变更、待审核分页。
 *
 * <p>跨聚合协作（申请建店、审核同步生效店铺状态）在应用层编排；
 * "一个用户至多一个商家"的不变量在领域服务 {@link MerchantRegistrar} 中保证；
 * 店铺资料读写见 {@link ShopManager}（ADR-019）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantManager {

    /** roleType：1=商家（授权域 setUserRole 的兼容口径，会同时保留 ROLE_USER） */
    private static final int ROLE_TYPE_SELLER = 1;

    private final MerchantRepository merchantRepository;
    private final ShopRepository shopRepository;
    private final MerchantRegistrar merchantRegistrar;
    private final AuthContextClient authContextClient;

    /**
     * 商家申请入驻：同一事务内创建商家身份（PENDING）+ 默认店铺（PENDING）。
     *
     * <p>事务边界在本方法（领域服务不含技术依赖）；并发重复申请由仓储层
     * 唯一键冲突翻译为 {@code USER_ALREADY_EXISTS}，事务整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(Long userId, MerchantApplyReq req) {
        Merchant merchant = Merchant.apply(userId);
        Shop defaultShop = Shop.open(req.getShopName(), null, req.getContactPhone(),
                req.getProvince(), req.getCity(), req.getDistrict(), req.getDetailAddress());
        merchantRegistrar.register(merchant, defaultShop);
        log.info("[merchant][apply][ok] userId={}, shopName={}", userId, req.getShopName());
    }

    /**
     * 审批通过：商家 + 默认店铺状态一并置正常，并经授权域自动授予卖家角色（ADR-018 缝合点）。
     *
     * <p>状态迁移一律走聚合方法（{@code Merchant#approve} / {@code Shop#approve}），
     * 不再直写状态列。不声明事务：本方法含 Feign 调用（禁止事务内调 Feign）；
     * 两次状态写入均幂等，角色授予失败抛异常后管理员重试即可收敛。
     */
    public void approve(Long id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "商家不存在"));
        merchant.approve();
        merchantRepository.updateStatus(merchant);
        shopRepository.findDefaultByMerchantId(merchant.getId()).ifPresent(shop -> {
            shop.approve();
            shopRepository.updateStatus(shop);
        });
        authContextClient.setUserRole(merchant.getUserId(), ROLE_TYPE_SELLER);
        log.info("[merchant][approve][ok] id={}, userId={}, 已授予 ROLE_SELLER", id, merchant.getUserId());
    }

    /**
     * 审核 / 变更商家状态（管理后台）—— 只接受合法迁移：
     * 置正常（审核通过或解冻）、置冻结；不能回退到审核中。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "商家不存在"));
        MerchantStatusEnum target = MerchantStatusEnum.fromCode(status);
        if (target == MerchantStatusEnum.NORMAL) {
            if (merchant.isFrozen()) {
                merchant.unfreeze();
            } else {
                merchant.approve();
            }
        } else if (target == MerchantStatusEnum.FROZEN) {
            merchant.freeze();
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的商家状态变更: " + target.getDescription());
        }
        merchantRepository.updateStatus(merchant);
    }

    /** 管理后台：分页查询待审核商家。 */
    public IPage<MerchantVO> pagePending(int pageNum, int size) {
        long total = merchantRepository.countByStatus(MerchantStatusEnum.PENDING);
        Page<MerchantVO> page = new Page<>(pageNum, size);
        page.setTotal(total);
        if (total == 0) {
            return page;
        }
        List<Merchant> merchants = merchantRepository.findPageByStatus(
                MerchantStatusEnum.PENDING, (pageNum - 1) * size, size);
        page.setRecords(merchants.stream().map(MerchantVO::from).toList());
        page.setPages((long) Math.ceil((double) total / size));
        return page;
    }
}
