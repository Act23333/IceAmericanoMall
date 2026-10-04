package org.icedamericanomall.ai.infrastructure.persistence.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessagePO> {
}
