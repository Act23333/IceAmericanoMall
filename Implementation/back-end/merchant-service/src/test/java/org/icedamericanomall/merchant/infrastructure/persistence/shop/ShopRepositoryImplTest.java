package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 店铺仓储实现单元测试 —— 业务唯一键（merchant_id + shop_name）冲突翻译为业务异常。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopRepositoryImpl 仓储单元测试")
class ShopRepositoryImplTest {

    @Mock
    private ShopMapper shopMapper;

    @InjectMocks
    private ShopRepositoryImpl shopRepository;

    private Shop shop() {
        Shop shop = Shop.open("冰美式旗舰店", null, "13800008000", "上海市", "上海市", "静安区", "南京西路 100 号");
        shop.assignMerchant(1L);
        return shop;
    }

    @Test
    @DisplayName("insert — uk_merchant_shop_name 冲突 → PARAM_ERROR（店铺名称已存在）")
    void shouldThrowBizException_whenShopNameConflict() {
        when(shopMapper.insert(any(ShopPO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_merchant_shop_name'"));

        BizException ex = assertThrows(BizException.class, () -> shopRepository.insert(shop()));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("店铺名称已存在", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("insert — 落库成功后回填技术主键与归属商家")
    void shouldReturnPersistedShop_whenInsertSucceeds() {
        when(shopMapper.insert(any(ShopPO.class))).thenAnswer(invocation -> {
            ShopPO dataObject = invocation.getArgument(0);
            dataObject.setId(11L);
            return 1;
        });

        Shop saved = shopRepository.insert(shop());

        assertEquals(11L, saved.getId());
        assertEquals(1L, saved.getMerchantId());
    }
}
