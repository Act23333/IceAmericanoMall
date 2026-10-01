package org.icedamericanomall.user.infrastructure.persistence.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.infrastructure.persistence.chat.ChatMessageDO;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
