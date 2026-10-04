package org.icedamericanomall.pay.adapter.assembler;

import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.pay.adapter.response.RefundVO;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.mapstruct.Mapper;

/**
 * 退款单 Domain → VO / 跨服务 CO 转换器（MapStruct）。
 *
 * <p>{@code RefundResultCO} 是 I40 的返回契约（字段子集 {@code refundNo + status}），
 * MapStruct 按同名属性自动映射，其余字段留空 —— 与契约的「子集」口径一致。
 */
@Mapper(componentModel = "spring")
public interface RefundConverter {

    /** 退款单 → 管理端视图（AD43）。 */
    RefundVO toVO(RefundOrder entity);

    /** 退款单 → I40 内部契约（字段子集：refundNo + status）。 */
    RefundResultCO toResultCO(RefundOrder entity);
}
