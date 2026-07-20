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
                .build();

        // 3. 写缓存
        // (a) UserInfo JSON 缓存 (30分钟)
        redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(userInfo), 30, TimeUnit.MINUTES);
        // (b) 权限 SET 缓存 (网关 SISMEMBER O(1) 查询)
        String permsKey = "user:perms:" + userId;
        Set<String> permsSet = new HashSet<>(perms);
        roles.forEach(role -> permsSet.add(role)); // 角色以 ROLE_ 前缀存入
        redisTemplate.delete(permsKey);
        redisTemplate.opsForSet().add(permsKey, permsSet.toArray(new String[0]));
        redisTemplate.expire(permsKey, 30, TimeUnit.MINUTES);

        return userInfo;
    }
}