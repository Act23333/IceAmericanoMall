package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.icedamericanomall.pay.infrastructure.persistence.RefundOrderPO;

/**
 * 退款单 Mapper —— 入参/出参均为 PO（本服务实体即 PO）。仅 BaseMapper 标准方法。
 */
public interface RefundOrderMapper extends BaseMapper<RefundOrderPO> {
}
