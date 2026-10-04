package org.icedamericanomall.ai.infrastructure.persistence.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家知识库 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface MerchantKnowledgeMapper extends BaseMapper<MerchantKnowledgePO> {
}
