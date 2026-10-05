package org.icedamericanomall.trade.domain.port;

/**
 * 开具入参 —— 发票单要素快照（Port 专用参数对象，不可变 record）。
 *
 * <p>税号/邮箱为<b>解密后明文</b>，仅用于样票票面渲染：只在内存流转、禁止落日志
 * （09-Constraints 脱敏约束）；仓储/VO 面永远只见三字段密文与掩码。
 *
 * @param invoiceNo 发票申请号（样票票面 + objectKey 组成部分）
 * @param orderNo   关联订单号（票面）
 * @param sellerId  开票主体商家 user_id（票面"销售方"）
 * @param type      抬头类型（1=个人 2=企业）
 * @param title     抬头（购买方名称）
 * @param taxNo     企业税号明文（个人抬头为 null）
 * @param email     交付邮箱明文（可空）
 * @param amount    开票金额（分）
 */
public record InvoiceIssueParam(String invoiceNo, String orderNo, Long sellerId,
                                int type, String title, String taxNo, String email, int amount) {
}
