package org.icedamericanomall.marketing.infrastructure.persistence.promotion;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 促销活动 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface PromotionMapper extends BaseMapper<PromotionPO> {
}
