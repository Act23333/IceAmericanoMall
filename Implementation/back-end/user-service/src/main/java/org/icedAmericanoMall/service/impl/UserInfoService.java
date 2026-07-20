package org.icedAmericanoMall.service.impl;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.mapper.PermissionMapper;
import org.icedAmericanoMall.mapper.RoleMapper;
import org.icedAmericanoMall.mapper.UserMapper;
import org.noLazy.common.domain.UserInfo;
import org.noLazy.common.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class UserInfoService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public UserInfo loadUserById(Long userId) {
        // 1. 先查缓存
        String cacheKey = "user:info:" + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, UserInfo.class);
        }

        // 2. 缓存未命中，查库聚合
        UserEntity user = userMapper.selectById(userId);
        Set<String> roles = roleMapper.selectRoleCodesByUserId(userId);
        Set<String> perms = new HashSet<>();
        perms.addAll(permissionMapper.selectDirectPermCodesByUserId(userId));
        perms.addAll(permissionMapper.selectRolePermCodesByUserId(userId));

        UserInfo userInfo = UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .permissions(perms)
                // ...其他字段
                .build();

        // 3. 写缓存（设置过期时间，例如30分钟）
        redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(userInfo), 30, TimeUnit.MINUTES);
        return userInfo;
    }
}