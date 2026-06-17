package org.icedAmericanoMall.service.impl;

import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserServiceImpl core business logic.
 *
 * Note: Methods using MyBatis-Plus lambdaQuery()/lambdaUpdate() chains (countUsers, updateProfile)
 * require {@code @SpringBootTest} integration tests due to the mapper proxy mechanism.
 * Those integration tests should use Testcontainers or H2 in-memory database.
 */
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    // ==================== 实体字段验证 ====================

    @Test
    @DisplayName("UserEntity — 字段赋值和读取正确")
    void shouldSetAndGetUserEntityFields() {
        UserEntity entity = new UserEntity();
        entity.setUserId("uuid-123");
        entity.setUsername("testuser");
        entity.setPhone("13800138000");
        entity.setRoleType(0);
        entity.setAvatar("https://example.com/avatar.jpg");

        assertEquals("uuid-123", entity.getUserId());
        assertEquals("testuser", entity.getUsername());
        assertEquals("13800138000", entity.getPhone());
        assertEquals(0, entity.getRoleType());
        assertEquals("https://example.com/avatar.jpg", entity.getAvatar());
    }

    @Test
    @DisplayName("UserEntity — roleType 默认值应为 null")
    void shouldHaveDefaultRoleType_whenNotSet() {
        UserEntity entity = new UserEntity();
        assertNull(entity.getRoleType());
    }

    @Test
    @DisplayName("UserEntity — id 自增主键类型验证")
    void shouldHaveLongId() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        assertInstanceOf(Long.class, entity.getId());
    }

    // ==================== 枚举验证 ====================

    @Test
    @DisplayName("UserStatusEnum — 状态码正确映射")
    void shouldHaveCorrectStatusCodes() {
        assertEquals(1, UserStatusEnum.NORMAL.getCode(), "正常状态码应为 1");
        assertEquals(0, UserStatusEnum.FROZEN.getCode(), "禁用状态码应为 0");
    }

    @Test
    @DisplayName("UserStatusEnum — 只有两个状态值")
    void shouldHaveExactlyTwoStatuses() {
        assertEquals(2, UserStatusEnum.values().length);
    }

    // ==================== UpdateProfileReq 验证 ====================

    @Test
    @DisplayName("UpdateProfileReq — 字段赋值正确")
    void shouldSetUpdateProfileReqFields() {
        var req = new org.icedAmericanoMall.domain.dto.UpdateProfileReq();
        req.setNickname("newname");
        req.setAvatar("https://example.com/avatar.jpg");

        assertEquals("newname", req.getNickname());
        assertEquals("https://example.com/avatar.jpg", req.getAvatar());
    }

    @Test
    @DisplayName("UpdateProfileReq — 默认所有字段为 null")
    void shouldHaveNullFields_whenNewInstance() {
        var req = new org.icedAmericanoMall.domain.dto.UpdateProfileReq();
        assertNull(req.getNickname());
        assertNull(req.getAvatar());
    }

    // ==================== DTO 序列化验证 ====================

    @Test
    @DisplayName("SellerRegisterReq — 实现 Serializable")
    void shouldBeSerializable_sellerRegisterReq() {
        var req = new org.icedAmericanoMall.domain.dto.SellerRegisterReq();
        req.setShopName("测试店铺");
        req.setContactPhone("13800138000");
        req.setProvince("广东省");
        req.setCity("深圳市");

        assertEquals("测试店铺", req.getShopName());
        assertEquals("13800138000", req.getContactPhone());
        assertTrue(req instanceof java.io.Serializable);
    }

    @Test
    @DisplayName("UpdateShopReq — 实现 Serializable")
    void shouldBeSerializable_updateShopReq() {
        var req = new org.icedAmericanoMall.domain.dto.UpdateShopReq();
        req.setShopName("新店名");
        req.setShopLogo("https://logo.png");
        assertTrue(req instanceof java.io.Serializable);
    }
}
