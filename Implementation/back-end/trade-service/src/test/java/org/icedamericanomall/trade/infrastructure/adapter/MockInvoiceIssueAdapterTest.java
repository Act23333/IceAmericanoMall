package org.icedamericanomall.trade.infrastructure.adapter;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.icedamericanomall.trade.domain.port.InvoiceIssueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟开具防腐适配器单元测试（决策 D2/O5）：模拟票号 {@code MOCK-} 前缀常量、
 * 样票 objectKey 命名空间、上传失败抛 OSS_UPLOAD_FAILED（开具失败不落状态）、
 * 预签名空键幂等（空键不触存储端口）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MockInvoiceIssueAdapter — 模拟开具防腐端口默认实现")
class MockInvoiceIssueAdapterTest {

    private static final String INVOICE_NO = "3f2a1b6c-0000-0000-0000-000000000001";
    private static final String BUCKET = "icedmall";
    private static final String PDF_KEY = "invoice/" + INVOICE_NO + ".pdf";

    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private StorageClient storageClient;

    private MockInvoiceIssueAdapter adapter;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket(BUCKET);
        adapter = new MockInvoiceIssueAdapter(pdfGenerator, storageClient, properties);
    }

    private InvoiceIssueParam param() {
        return new InvoiceIssueParam(INVOICE_NO, "ORD-1", 200L, 1, "张三", null, null, 100);
    }

    @Test
    @DisplayName("开具 — 模拟票号 MOCK- 前缀 + 8 位片段；样票上传至 invoice/{invoiceNo}.pdf")
    void shouldUploadSamplePdfAndReturnMockNo_whenIssue() {
        when(pdfGenerator.generate(any(InvoiceIssueParam.class), anyString()))
                .thenReturn("%PDF-1.4 mock".getBytes(StandardCharsets.US_ASCII));
        when(storageClient.upload(eq(BUCKET), eq(PDF_KEY), any(InputStream.class),
                eq("application/pdf"))).thenReturn("http://minio/ok");

        InvoiceIssueResult result = adapter.issue(param());

        assertTrue(result.mockInvoiceNo().startsWith("MOCK-"), "模拟票号前缀: " + result.mockInvoiceNo());
        assertEquals("MOCK-", MockInvoiceIssueAdapter.MOCK_INVOICE_NO_PREFIX);
        assertEquals(13, result.mockInvoiceNo().length(), "MOCK- + 8 位 UUID 片段");
        assertEquals(PDF_KEY, result.pdfKey());
    }

    @Test
    @DisplayName("开具 — 样票上传失败（存储返回 null）→ OSS_UPLOAD_FAILED，不产出结果")
    void shouldThrow_whenUploadFails() {
        when(pdfGenerator.generate(any(InvoiceIssueParam.class), anyString()))
                .thenReturn("%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        when(storageClient.upload(anyString(), anyString(), any(InputStream.class), anyString()))
                .thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> adapter.issue(param()));
        assertEquals(ErrorCode.OSS_UPLOAD_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("预签名 — 空 objectKey 幂等返回 null；有效键委托存储端口（30 分钟有效期）")
    void shouldPresign_whenKeyPresent() {
        assertNull(adapter.presignDownloadUrl(null));
        assertNull(adapter.presignDownloadUrl(" "));
        verify(storageClient, never()).getPresignedUrl(anyString(), anyString(), anyInt());

        when(storageClient.getPresignedUrl(BUCKET, PDF_KEY, 30)).thenReturn("https://signed");
        assertEquals("https://signed", adapter.presignDownloadUrl(PDF_KEY));
    }
}
