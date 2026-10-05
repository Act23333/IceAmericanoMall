package org.icedamericanomall.trade.domain.model.invoice;

import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票聚合守卫矩阵单元测试（F14，PRD §15.5.1 / §15.4.1）。
 *
 * <p>覆盖：创建自封装守卫（类型/抬头/企业税号必填/金额）、PII 三字段落值
 * （cipher/hash/mask 持久化面 + 明文仅内存）、状态机前置守卫
 * （开具/买家撤回/作废合规守卫「已开具不可作废」/冲红）。
 */
@DisplayName("Invoice 聚合 — 创建守卫与状态机前置守卫矩阵")
class InvoiceTest {

    private static final String ORDER_NO = "ORD-20261005-001";
    private static final long USER_ID = 100L;
    private static final long SELLER_ID = 200L;
    private static final String TAX_NO = "91110000123456789X";
    private static final String EMAIL = "buyer@example.com";

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    private Invoice pendingPersonal() {
        return Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, EMAIL, 12550, null);
    }

    private Invoice issued() {
        Invoice invoice = pendingPersonal();
        invoice.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        return invoice;
    }

    @Nested
    @DisplayName("创建自封装守卫")
    class CreateGuards {

        @Test
        @DisplayName("个人抬头创建成功 — 状态待开具 + 有效票标记 + 金额快照")
        void shouldCreatePendingInvoice_whenPersonalTitleValid() {
            Invoice invoice = pendingPersonal();

            assertEquals(InvoiceStatusEnum.PENDING_ISSUE.getCode(), invoice.getStatus());
            assertEquals(1, invoice.getActiveFlag());
            assertEquals(12550, invoice.getAmount());
            assertEquals(ORDER_NO, invoice.getOrderNo());
            assertEquals(SELLER_ID, invoice.getSellerId());
            assertNotNull(invoice.getInvoiceNo(), "发票申请号（UUID）必须生成");
            assertNull(invoice.getTaxNo(), "个人抬头无税号");
        }

        @Test
        @DisplayName("企业抬头缺税号 → 拒绝（PARAM_ERROR，不创建聚合）")
        void shouldReject_whenCompanyWithoutTaxNo() {
            BizException ex = assertThrows(BizException.class,
                    () -> Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 2, "某某公司", " ", null, 12550, null));
            assertTrue(ex.getMessage().contains("税号"));
        }

        @Test
        @DisplayName("抬头类型非法 / 抬头为空 / 抬头超长 / 金额非正 → 全部拒绝")
        void shouldReject_whenBasicFieldsInvalid() {
            assertThrows(BizException.class, () ->
                    Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 9, "张三", null, null, 100, null));
            assertThrows(BizException.class, () ->
                    Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, " ", null, null, 100, null));
            assertThrows(BizException.class, () ->
                    Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "长".repeat(101), null, null, 100, null));
            assertThrows(BizException.class, () ->
                    Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, null, 0, null));
        }

        @Test
        @DisplayName("PII 三字段落值 — 税号/邮箱 cipher/hash/mask 持久化面齐备，明文可解密回读")
        void shouldPopulateThreeFieldPii_whenSensitiveProvided() {
            Invoice invoice = Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 2, "某某公司",
                    TAX_NO, EMAIL, 12550, null);

            EncryptedIdentifier taxNo = invoice.getTaxNo();
            assertNotNull(taxNo);
            assertNotNull(taxNo.getCipher(), "密文必须落值");
            assertNotNull(taxNo.getHash(), "盲索引必须落值");
            assertTrue(taxNo.getMask().contains("*"), "脱敏展示必须掩码化: " + taxNo.getMask());
            assertFalse(taxNo.getMask().equals(TAX_NO), "脱敏值不得等于明文");
            assertEquals(TAX_NO, taxNo.getPlain(), "明文仅内存可解密回读");

            EncryptedIdentifier email = invoice.getEmail();
            assertNotNull(email);
            assertNotNull(email.getCipher());
            assertNotNull(email.getHash());
            assertTrue(email.getMask().contains("*"));
            assertEquals(EMAIL, email.getPlain());
        }

        @Test
        @DisplayName("个人抬头不采集税号；空邮箱不落 PII 三字段")
        void shouldSkipEmailPii_whenEmailBlank() {
            Invoice invoice = Invoice.create(ORDER_NO, USER_ID, SELLER_ID, 1, "张三", null, "  ", 100, null);
            assertNull(invoice.getTaxNo());
            assertNull(invoice.getEmail());
        }
    }

    @Nested
    @DisplayName("状态机前置守卫")
    class StatusGuards {

        @Test
        @DisplayName("开具 — 仅待开具可开具；已开具/终态拒绝")
        void shouldGuardIssueOnlyFromPending() {
            assertDoesNotThrow(pendingPersonal()::assertIssueable);

            BizException issuedEx = assertThrows(BizException.class, issued()::assertIssueable);
            assertTrue(issuedEx.getMessage().contains("待开具"));
            assertThrows(BizException.class, () -> guardFrom(InvoiceStatusEnum.RED_FLUSHED).assertIssueable());
        }

        @Test
        @DisplayName("买家撤回 — 仅待开具可撤回")
        void shouldGuardBuyerCancelOnlyFromPending() {
            assertDoesNotThrow(pendingPersonal()::assertBuyerCancellable);
            BizException ex = assertThrows(BizException.class, issued()::assertBuyerCancellable);
            assertTrue(ex.getMessage().contains("撤回"));
        }

        @Test
        @DisplayName("作废合规守卫 — 已开具不可作废，只能冲红（PRD 守卫清单 #1）")
        void shouldRejectVoid_whenIssued() {
            assertDoesNotThrow(pendingPersonal()::assertVoidable);

            BizException ex = assertThrows(BizException.class, issued()::assertVoidable);
            assertTrue(ex.getMessage().contains("已开具发票不可作废"), "合规守卫文案: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("冲红"));
        }

        @Test
        @DisplayName("冲红 — 仅已开具可冲红；待开具拒绝")
        void shouldGuardRedFlushOnlyFromIssued() {
            assertDoesNotThrow(issued()::assertRedFlushable);

            BizException ex = assertThrows(BizException.class, pendingPersonal()::assertRedFlushable);
            assertTrue(ex.getMessage().contains("已开具"));
        }
    }

    /** 构造指定状态的聚合（守卫矩阵驱动）。 */
    private Invoice guardFrom(InvoiceStatusEnum status) {
        Invoice invoice = pendingPersonal();
        invoice.setStatus(status.getCode());
        return invoice;
    }
}
