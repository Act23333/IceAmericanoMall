package org.icedamericanomall.auth.infrastructure.persistence.userauth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * user_auth 表 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuthPO> {

    /**
     * 按业务唯一键（identity_type + 非敏感标识 或 盲索引）插入或更新。
     *
     * <p>依赖库表唯一索引 {@code uk_type_hash} / {@code uk_type_plain}：
     * 命中已有记录时只更新可变字段，保证并发下的幂等写入。
     *
     * @param entity PO（id 与 createTime 不参与更新）
     * @return 受影响行数
     */
    @Update("""
            INSERT INTO user_auth
                (user_id, identity_type, identifier_plain, identifier_cipher, identifier_hash,
                 identifier_mask, union_id, credential, status, last_login_time, create_time, update_time)
            VALUES
                (#{userId}, #{identityType}, #{identifierPlain}, #{identifierCipher}, #{identifierHash},
                 #{identifierMask}, #{unionId}, #{credential}, #{status}, #{lastLoginTime}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                user_id         = VALUES(user_id),
                union_id        = COALESCE(VALUES(union_id), union_id),
                credential      = COALESCE(VALUES(credential), credential),
                status          = VALUES(status),
                last_login_time = COALESCE(VALUES(last_login_time), last_login_time),
                update_time     = NOW()
            """)
    int upsert(@Param("entity") UserAuthPO entity);
}
