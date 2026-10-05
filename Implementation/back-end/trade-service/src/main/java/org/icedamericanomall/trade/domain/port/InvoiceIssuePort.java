package org.icedamericanomall.trade.domain.port;

/**
 * 发票开具领域端口（防腐层，F14 Phase 1 模拟开具，PRD §15.8.5）。
 *
 * <p><b>防腐定位</b>：开具是与外部开票服务（Phase 1 = 本地模拟开票适配器；Phase 2 = 税局/
 * 电子发票服务商）的协作边界 —— 领域/应用层只依赖本端口，Phase 2 替换 infrastructure 侧
 * 适配器即可接入真实开票，app/domain 零改动（对齐 ai-service 领域端口先例 ADR-039）。
 *
 * <p><b>样票存储</b>：模拟开具产出的简化 PDF 样票上传对象存储，端口同时暴露样票
 * 预签名下载地址解析（下载是同一外部能力的读取面，收口同一端口避免适配器扩散）。
 */
public interface InvoiceIssuePort {

    /**
     * 执行（模拟）开具 —— 生成模拟票号与简化 PDF 样票（水印"样票"）并上传对象存储。
     *
     * @param param 发票单要素（抬头/税号/金额等快照）
     * @return 开具结果（模拟票号 + 样票 objectKey）
     * @throws org.icedamericanomall.common.exception.BizException 样票生成/上传失败
     *         （OSS_UPLOAD_FAILED，开具失败不落状态）
     */
    InvoiceIssueResult issue(InvoiceIssueParam param);

    /**
     * 解析样票预签名下载地址（仅已开具发票有 objectKey）。
     *
     * @param pdfKey 样票 objectKey（可空）
     * @return 预签名下载 URL；入参为空返回 null
     */
    String presignDownloadUrl(String pdfKey);
}
