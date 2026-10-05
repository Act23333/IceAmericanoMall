package org.icedamericanomall.trade.domain.model.invoice;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 常用发票抬头聚合单元测试（INV6-9 域守卫）：自封装校验（类型/抬头/企业税号必填）、
 * PII 三字段加密（maskAccount 尾 4 口径）、全量更新清列语义（企业转个人清税号）。
 * 不依赖任何框架，直接 new。
 */
@DisplayName("InvoiceTitle — 抬头聚合自封装守卫与全量更新")
class InvoiceTitleTest {

    private static final long USER_ID = 100L;
    private static final String TAX_NO = "91110000123456789X";

    @BeforeAll
    static void initCrypto() {
        // 税号三字段加密需要 AES/HMAC 密钥（InvoiceBuyerManagerTest 同款预置）
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    @DisplayName("创建 — 个人抬头忽略报文税号（PII 从严），非默认 is_default=null")
    void shouldIgnoreTaxNo_whenCreatePersonal() {
        InvoiceTitle title = InvoiceTitle.create(USER_ID, 1, "张三", TAX_NO, false);

        assertEquals(USER_ID, title.getUserId());
        assertEquals(1, title.getType());
        assertEquals("张三", title.getTitle());
        assertNull(title.getTaxNo(), "个人抬头不落税号");
        assertNull(title.getIsDefault());
    }

    @Test
    @DisplayName("创建 — 企业抬头税号加密为三字段，掩码为尾 4 口径（不落明文）")
    void shouldEncryptTaxNo_whenCreateCompany() {
        InvoiceTitle title = InvoiceTitle.create(USER_ID, 2, "某公司", TAX_NO, true);

        assertNotNull(title.getTaxNo());
        assertNotNull(title.getTaxNo().getCipher());
        assertNotNull(title.getTaxNo().getHash());
        assertEquals("****789X", title.getTaxNo().getMask());
        assertNotEquals(TAX_NO, title.getTaxNo().getCipher());
        assertEquals(InvoiceTitle.DEFAULT_FLAG, title.getIsDefault());
        assertTrue(title.isDefaultTitle());
    }

    @Test
    @DisplayName("创建 — 企业缺税号 → PARAM_ERROR")
    void shouldReject_whenCompanyTaxNoMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> InvoiceTitle.create(USER_ID, 2, "某公司", " ", false));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("创建 — 类型非法 / 抬头缺失 / 抬头超长 → PARAM_ERROR")
    void shouldReject_whenTitleFieldsInvalid() {
        assertThrows(BizException.class, () -> InvoiceTitle.create(USER_ID, 3, "张三", null, false));
        assertThrows(BizException.class, () -> InvoiceTitle.create(USER_ID, 1, " ", null, false));
        assertThrows(BizException.class, () ->
                InvoiceTitle.create(USER_ID, 1, "长".repeat(Invoice.TITLE_MAX_LENGTH + 1), null, false));
    }

    @Test
    @DisplayName("全量更新 — 企业转个人清税号（NULL 清列语义）并按报文替换全部要素")
    void shouldClearTaxNo_whenCompanyTurnsPersonal() {
        InvoiceTitle title = InvoiceTitle.create(USER_ID, 2, "某公司", TAX_NO, true);

        title.applyFullUpdate(1, "张三", null, false);

        assertEquals(1, title.getType());
        assertEquals("张三", title.getTitle());
        assertNull(title.getTaxNo(), "转个人后税号必须清空");
        assertNull(title.getIsDefault());
    }

    @Test
    @DisplayName("全量更新 — 个人转企业税号必填；更新后可解密还原（INV1 引用链路依赖）")
    void shouldReencrypt_whenPersonalTurnsCompany() {
        InvoiceTitle title = InvoiceTitle.create(USER_ID, 1, "张三", null, false);

        assertThrows(BizException.class, () -> title.applyFullUpdate(2, "某公司", null, false));

        title.applyFullUpdate(2, "某公司", TAX_NO, false);
        assertEquals(2, title.getType());
        assertEquals(TAX_NO, title.getTaxNo().getPlain(), "INV1 引用抬头时需解密取明文快照");
    }
}
