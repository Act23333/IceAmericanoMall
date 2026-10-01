package org.icedamericanomall.user.domain.model;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.domain.port.PasswordVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户聚合根纯单元测试（不依赖任何框架，直接 new / 工厂构造）。
 *
 * <p>覆盖：状态迁移（禁用/启用）、凭证校验、资料变更、余额不变量、状态枚举映射。
 */
@DisplayName("User 聚合根领域单元测试")
class UserTest {

    /** 测试替身：明文比较（哈希实现属基础设施职责） */
    private final PasswordVerifier passwordVerifier = new PasswordVerifier() {
        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword != null && encodedPassword.contentEquals(rawPassword);
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }
    };

    // ==================== 状态迁移 ====================

    @Test
    @DisplayName("disable — 状态迁移为 FROZEN 且幂等")
    void shouldFreeze_whenDisable() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertEquals(UserStatusEnum.NORMAL, user.getStatus());

        user.disable();
        assertEquals(UserStatusEnum.FROZEN, user.getStatus());
        assertTrue(user.isFrozen());
        assertNotNull(user.getUpdateTime(), "状态迁移应刷新 updateTime");

        user.disable();
        assertEquals(UserStatusEnum.FROZEN, user.getStatus(), "重复禁用幂等");
    }

    @Test
    @DisplayName("enable — 冻结后恢复 NORMAL")
    void shouldResume_whenEnable() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.disable();
        user.enable();
        assertEquals(UserStatusEnum.NORMAL, user.getStatus());
        assertFalse(user.isFrozen());
    }

    @Test
    @DisplayName("assertActive — 冻结用户抛 USER_STATUS_ABNORMAL")
    void shouldThrow_whenFrozen() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.disable();
        BizException e = assertThrows(BizException.class, user::assertActive);
        assertEquals(ErrorCode.USER_STATUS_ABNORMAL, e.getErrorCode());
    }

    // ==================== 凭证 ====================

    @Test
    @DisplayName("verifyPassword — 哈希匹配决策委托 PasswordVerifier 端口")
    void shouldVerifyPassword_viaPort() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertFalse(user.verifyPassword("secret", passwordVerifier), "无凭证时校验失败");

        user.changePassword(passwordVerifier.encode("secret"));
        assertTrue(user.verifyPassword("secret", passwordVerifier));
        assertFalse(user.verifyPassword("wrong", passwordVerifier));
    }

    // ==================== 资料变更 ====================

    @Test
    @DisplayName("changeNickname / changeAvatar — 更新字段并刷新 updateTime")
    void shouldChangeProfile() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.changeNickname("冰美式");
        user.changeAvatar("https://cdn/avatar.jpg");
        assertEquals("冰美式", user.getUsername());
        assertEquals("https://cdn/avatar.jpg", user.getAvatar());
        assertNotNull(user.getUpdateTime());
    }

    @Test
    @DisplayName("markLoginSuccess — 记录最近登录时间")
    void shouldMarkLoginSuccess() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertNull(user.getLastLoginTime());
        user.markLoginSuccess();
        assertNotNull(user.getLastLoginTime());
    }

    // ==================== 余额不变量 ====================

    @Test
    @DisplayName("assertBalanceSufficient — 余额充足放行")
    void shouldPassBalanceCheck_whenSufficient() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.setBalance(10000);
        user.assertBalanceSufficient(3000);
    }

    @Test
    @DisplayName("assertBalanceSufficient — 余额不足抛 BALANCE_INSUFFICIENT")
    void shouldThrow_whenBalanceInsufficient() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.setBalance(100);
        BizException e = assertThrows(BizException.class, () -> user.assertBalanceSufficient(999));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, e.getErrorCode());
    }

    @Test
    @DisplayName("assertBalanceSufficient — 非正金额抛 PARAM_ERROR")
    void shouldThrow_whenAmountNotPositive() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        BizException e = assertThrows(BizException.class, () -> user.assertBalanceSufficient(0));
        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
    }

    // ==================== 工厂与枚举 ====================

    @Test
    @DisplayName("create — PHONE 落 phone 列；WECHAT 落 wx_openid；默认状态/余额")
    void shouldCreateByIdentityType() {
        User phoneUser = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertEquals("13800138000", phoneUser.getPhone());
        assertNull(phoneUser.getWxOpenid());
        assertNotNull(phoneUser.getUserId(), "业务 UUID 由工厂生成");

        User wechatUser = User.create(IdentityTypeEnum.WECHAT, "wx-openid-1");
        assertEquals("wx-openid-1", wechatUser.getWxOpenid());
        assertNull(wechatUser.getPhone());
        assertEquals(0, wechatUser.getBalance());
        assertEquals(0, wechatUser.getRoleType());
    }

    @Test
    @DisplayName("UserStatusEnum — 状态码映射与非法值拒绝")
    void shouldMapStatusCodes() {
        assertEquals(1, UserStatusEnum.NORMAL.getCode(), "正常状态码应为 1");
        assertEquals(0, UserStatusEnum.FROZEN.getCode(), "禁用状态码应为 0");
        assertEquals(2, UserStatusEnum.values().length);
        assertEquals(UserStatusEnum.NORMAL, UserStatusEnum.fromCode(1));
        assertNull(UserStatusEnum.fromCode(null));
        assertThrows(BadRequestException.class, () -> UserStatusEnum.of(99));
    }
}
