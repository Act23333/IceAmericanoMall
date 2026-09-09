package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.icedamericanomall.domain.entity.NotificationEntity;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE id = #{id} AND recipient_id = #{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notification SET is_read = 1 WHERE recipient_id = #{userId}")
    int markAllRead(@Param("userId") Long userId);
}
