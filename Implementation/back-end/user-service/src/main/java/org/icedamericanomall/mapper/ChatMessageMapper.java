package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.domain.entity.ChatMessageEntity;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
