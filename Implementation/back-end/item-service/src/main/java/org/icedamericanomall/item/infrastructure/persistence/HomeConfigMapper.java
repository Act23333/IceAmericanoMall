package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.item.infrastructure.persistence.HomeConfigPO;

@Mapper
public interface HomeConfigMapper extends BaseMapper<HomeConfigPO> {
}
