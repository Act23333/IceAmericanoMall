package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 发票联动补偿 Mapper —— 入参/出参均为 PO（本服务实体即 PO）。
 *
 * <p>本批只有写入需求（联动失败落一条 PENDING），读侧（{@code status=PENDING AND next_retry_at<=now()}
 * 的批量拾取、退避更新）随重试任务批次引入，届时用显式 SQL 收口在 Mapper 层
 * （与 {@link RefundOrderMapper#selectStaleProcessing} 同口径），不在 Wrapper 里拼筛选口径。
 */
public interface InvoiceLinkCompensationMapper extends BaseMapper<InvoiceLinkCompensationPO> {
}
