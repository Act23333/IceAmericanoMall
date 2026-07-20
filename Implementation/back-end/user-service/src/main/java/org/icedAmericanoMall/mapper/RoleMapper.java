package org.icedAmericanoMall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface RoleMapper {
    @Select("SELECT r.code FROM roles r INNER JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    Set<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}