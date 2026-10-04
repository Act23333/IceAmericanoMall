package org.icedamericanomall.merchant.domain.service;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商家入驻领域服务单元测试 —— 商家 + 默认店铺成对创建、唯一性不变量与并发冲突翻译。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantRegistrar 领域服务单元测试")
class MerchantRegistrarTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ShopRepository shopRepository;

    private MerchantRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new MerchantRegistrar(merchantRepository, shopRepository);
    }

    private Shop defaultShop() {
        return Shop.open("冰美式旗舰店", null, "13800008000", "上海市", "上海市", "静安区", "南京西路 100 号");
    }

    @Test
    @DisplayName("入驻 — 创建商家身份 + 默认店铺，店铺归属商家技术主键")
    void shouldCreateMerchantAndDefaultShop_whenApply() {
        Merchant merchant = Merchant.apply(100L);
        Shop defaultShop = defaultShop();
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(merchantRepository.insert(merchant))
                .thenReturn(Merchant.builder().id(9L).userId(100L).status(MerchantStatusEnum.PENDING).build());

        Merchant saved = registrar.register(merchant, defaultShop);

        assertEquals(9L, saved.getId());
        assertEquals(9L, defaultShop.getMerchantId(), "归属由领域服务回填");
        assertEquals(ShopStatusEnum.PENDING, defaultShop.getStatus());
        verify(shopRepository).insert(defaultShop);
    }

    @Test
    @DisplayName("入驻 — 已有商家记录 → USER_ALREADY_EXISTS，不落任何数据")
    void shouldReject_whenMerchantAlreadyExists() {
        Merchant existing = Merchant.builder().id(1L).userId(100L).build();
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(existing));

        BizException ex = assertThrows(BizException.class,
                () -> registrar.register(Merchant.apply(100L), defaultShop()));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
        verify(merchantRepository, never()).insert(any(Merchant.class));
        verify(shopRepository, never()).insert(any(Shop.class));
    }

    @Test
    @DisplayName("入驻 — 并发重复申请（唯一键冲突）→ USER_ALREADY_EXISTS，不建店")
    void shouldThrowUserAlreadyExists_whenConcurrentApplyConflicts() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.empty());
        when(merchantRepository.insert(any(Merchant.class)))
                .thenThrow(new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家"));

        BizException ex = assertThrows(BizException.class,
                () -> registrar.register(Merchant.apply(100L), defaultShop()));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
        verify(shopRepository, never()).insert(any(Shop.class));
    }
}
