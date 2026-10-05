package org.icedamericanomall.trade.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.port.InvoiceIssueResult;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * 模拟开具防腐适配器（Phase 1，决策 D2/O5）—— {@link InvoiceIssuePort} 默认实现。
 *
 * <p><b>开具语义</b>：模拟票号 = {@code MOCK-} 前缀 + UUID 片段（常量化）；样票 =
 * {@link InvoicePdfGenerator} 简化 PDF（水印"样票"，非税务效力）上传 MinIO。
 * <b>Phase 2 替换点</b>：税局/电子发票服务商适配器实现同一端口即可整体替换，
 * app/domain 零改动（PRD §15.8.5 防腐设计方向）。
 *
 * <p><b>失败语义</b>：样票上传失败（存储侧返回 null，对齐 {@code AvatarStorageAdapter} 降级口径）
 * 抛 {@code OSS_UPLOAD_FAILED} —— 开具失败不落状态（管理端可直接重试，无脏态）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockInvoiceIssueAdapter implements InvoiceIssuePort {

    /** 模拟票号前缀（Phase 2 语义替换为服务商票号）。 */
    public static final String MOCK_INVOICE_NO_PREFIX = "MOCK-";

    /** 模拟票号取 UUID 前 8 位（大写，片段常量口径）。 */
    private static final int UUID_SNIPPET_LENGTH = 8;

    /** 样票 objectKey 前缀（对象命名空间）。 */
    private static final String PDF_OBJECT_PREFIX = "invoice/";

    /** 样票 objectKey 后缀。 */
    private static final String PDF_OBJECT_SUFFIX = ".pdf";

    /** 样票内容类型。 */
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    /** 预签名下载地址有效期（分钟）。 */
    private static final int PRESIGN_EXPIRY_MINUTES = 30;

    private final InvoicePdfGenerator pdfGenerator;
    private final StorageClient storageClient;
    private final StorageProperties storageProperties;

    @Override
    public InvoiceIssueResult issue(InvoiceIssueParam param) {
        String mockInvoiceNo = MOCK_INVOICE_NO_PREFIX
                + UUID.randomUUID().toString().replace("-", "")
                        .substring(0, UUID_SNIPPET_LENGTH).toUpperCase();
        byte[] pdf = pdfGenerator.generate(param, mockInvoiceNo);
        String objectKey = PDF_OBJECT_PREFIX + param.invoiceNo() + PDF_OBJECT_SUFFIX;
        String url = storageClient.upload(storageProperties.getBucket(), objectKey,
                new ByteArrayInputStream(pdf), PDF_CONTENT_TYPE);
        if (url == null) {
            throw new BizException(ErrorCode.OSS_UPLOAD_FAILED, "发票样票上传失败");
        }
        log.info("[invoice-issue][mock][done] invoiceNo={} mockInvoiceNo={} objectKey={}",
                param.invoiceNo(), mockInvoiceNo, objectKey);
        return new InvoiceIssueResult(mockInvoiceNo, objectKey);
    }

    @Override
    public String presignDownloadUrl(String pdfKey) {
        if (pdfKey == null || pdfKey.isBlank()) {
            return null;
        }
        return storageClient.getPresignedUrl(storageProperties.getBucket(), pdfKey, PRESIGN_EXPIRY_MINUTES);
    }
}
