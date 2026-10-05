package org.icedamericanomall.trade.infrastructure.adapter;

import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发票样票 PDF 生成器单元测试（决策 O5）：断言产物为非空 PDF 字节（{@code %PDF-} 版本头），
 * 含"样票"水印字样的位图版式由 {@link InvoiceSampleRenderer} 渲染（此处锁定开票要素进入票面）。
 */
@DisplayName("InvoicePdfGenerator — 样票 PDF 字节产物")
class InvoicePdfGeneratorTest {

    private final InvoicePdfGenerator generator =
            new InvoicePdfGenerator(new InvoiceSampleRenderer());

    @Test
    @DisplayName("生成 — 企业抬头要素（税号/金额/票号）→ 非空 PDF 字节且版本头正确")
    void shouldProducePdfBytes_whenCompanyInvoice() {
        InvoiceIssueParam param = new InvoiceIssueParam(
                "3f2a1b6c-0000-0000-0000-000000000001", "ORD-1", 200L,
                2, "某某公司", "91110000123456789X", "buyer@example.com", 12550);

        byte[] pdf = generator.generate(param, "MOCK-ABCDEF12");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0, "PDF 字节非空");
        String header = new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF-"), "PDF 版本头必须正确，实际: " + header);
    }

    @Test
    @DisplayName("生成 — 个人抬头（无税号）同样出票")
    void shouldProducePdfBytes_whenPersonalInvoice() {
        InvoiceIssueParam param = new InvoiceIssueParam(
                "3f2a1b6c-0000-0000-0000-000000000002", "ORD-2", 200L,
                1, "张三", null, null, 8800);

        byte[] pdf = generator.generate(param, "MOCK-12345678");

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertTrue(pdf[0] == '%' && pdf[1] == 'P', "首两字节必须是 %P（PDF 头）");
    }
}
