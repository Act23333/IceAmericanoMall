package org.icedamericanomall.trade.domain.port;

/**
 * 开具出参 —— 模拟开具结果（不可变 record）。
 *
 * @param mockInvoiceNo 模拟票号（{@code MOCK-} 前缀 + UUID 片段；Phase 2 语义替换为服务商票号）
 * @param pdfKey        样票 objectKey（MinIO，供买家下载与预签名解析）
 */
public record InvoiceIssueResult(String mockInvoiceNo, String pdfKey) {
}
