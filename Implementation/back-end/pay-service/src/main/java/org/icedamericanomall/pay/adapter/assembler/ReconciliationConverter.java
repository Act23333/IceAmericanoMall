package org.icedamericanomall.pay.adapter.assembler;

import org.icedamericanomall.pay.adapter.response.ReconciliationBatchVO;
import org.icedamericanomall.pay.adapter.response.ReconciliationDetailVO;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationBatch;
import org.icedamericanomall.pay.domain.model.reconciliation.ReconciliationDetail;
import org.mapstruct.Mapper;

/**
 * 对账批次 / 明细 Domain → VO 转换器（MapStruct）。
 */
@Mapper(componentModel = "spring")
public interface ReconciliationConverter {

    /** 对账批次 → 管理端视图（AD44 / AD46）。 */
    ReconciliationBatchVO toVO(ReconciliationBatch entity);

    /** 对账明细 → 管理端视图（AD45）。 */
    ReconciliationDetailVO toVO(ReconciliationDetail entity);
}
