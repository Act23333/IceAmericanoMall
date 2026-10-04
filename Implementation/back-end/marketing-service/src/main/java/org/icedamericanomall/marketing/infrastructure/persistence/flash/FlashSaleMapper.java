package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀活动 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface FlashSaleMapper extends BaseMapper<FlashSalePO> {
}
