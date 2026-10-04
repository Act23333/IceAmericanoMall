package org.icedamericanomall.notification.infrastructure.persistence.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内信 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface InAppMessageMapper extends BaseMapper<InAppMessagePO> {
}
