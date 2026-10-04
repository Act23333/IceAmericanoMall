package org.icedamericanomall.merchant.infrastructure.persistence.seller;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
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
 * 商家仓储实现单元测试 —— 唯一键冲突（并发重复申请）翻译为业务异常。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantRepositoryImpl 仓储单元测试")
class MerchantRepositoryImplTest {

    @Mock
    private MerchantMapper sellerMapper;

    @InjectMocks
    private MerchantRepositoryImpl merchantRepository;

    @Test
    @DisplayName("insert — uk_user_id 唯一键冲突 → USER_ALREADY_EXISTS（不裸抛框架异常）")
    void shouldThrowUserAlreadyExists_whenUniqueKeyConflict() {
        when(sellerMapper.insert(any(MerchantPO.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_user_id'"));

        BizException ex = assertThrows(BizException.class,
                () -> merchantRepository.insert(Merchant.apply(100L)));

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("insert — 落库成功后回填技术主键")
    void shouldReturnPersistedMerchant_whenInsertSucceeds() {
        when(sellerMapper.insert(any(MerchantPO.class))).thenAnswer(invocation -> {
            MerchantPO dataObject = invocation.getArgument(0);
            dataObject.setId(7L);
            return 1;
        });

        Merchant saved = merchantRepository.insert(Merchant.apply(100L));

        assertEquals(7L, saved.getId());
        assertEquals(100L, saved.getUserId());
    }
}
