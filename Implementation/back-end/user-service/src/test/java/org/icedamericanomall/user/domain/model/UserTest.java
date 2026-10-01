package org.icedamericanomall.user.domain.model;

import org.icedamericanomall.user.domain.enums.GenderEnum;
import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户聚合根纯单元测试（不依赖任何框架，直接 new / 工厂构造）。
 *
 * <p>V5.2 覆盖：状态迁移、资料变更（含 gender/birthday/memberLevel）、
 * 标识换绑（手机号/邮箱格式校验与三字段替换）、余额不变量、工厂与枚举映射；
 * 凭证行为（password / wxOpenid / roleType）已移除（归 user_auth / RBAC 五表，ADR-017）。
 */
@DisplayName("User 聚合根领域单元测试")
class UserTest {

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

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

    // ==================== 资料变更 ====================

    @Test
    @DisplayName("changeNickname / changeAvatar — 更新字段并刷新 updateTime")
    void shouldChangeProfile() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.changeNickname("冰美式");
        user.changeAvatar("https://cdn/avatar.jpg");
        assertEquals("冰美式", user.getNickname());
        assertEquals("https://cdn/avatar.jpg", user.getAvatar());
        assertNotNull(user.getUpdateTime());
    }

    @Test
    @DisplayName("changeGender — 合法取值（0/1/2）写入；非法取值抛 PARAM_ERROR")
    void shouldChangeGender_withValidation() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertEquals(GenderEnum.UNKNOWN, user.getGender(), "工厂缺省性别=未知");

        user.changeGender(GenderEnum.FEMALE);
        assertEquals(GenderEnum.FEMALE, user.getGender());

        assertThrows(BizException.class, () -> user.changeGender(null));
    }

    @Test
    @DisplayName("changeBirthday — 合法日期写入；未来日期拒绝")
    void shouldChangeBirthday_withValidation() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        LocalDate birthday = LocalDate.of(1998, 3, 15);
        user.changeBirthday(birthday);
        assertEquals(birthday, user.getBirthday());

        assertThrows(BizException.class, () -> user.changeBirthday(LocalDate.now().plusDays(1)));
        user.changeBirthday(null);
        assertNull(user.getBirthday(), "允许清空生日");
    }

    @Test
    @DisplayName("changeMemberLevel — fromCode 归一化大小写；空白/未知 code 拒绝")
    void shouldChangeMemberLevel_withNormalization() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertEquals(MemberLevelEnum.NORMAL, user.getMemberLevel(), "工厂缺省等级=NORMAL");

        user.changeMemberLevel(MemberLevelEnum.fromCode("gold"));
        assertEquals(MemberLevelEnum.GOLD, user.getMemberLevel());

        assertThrows(BizException.class, () -> user.changeMemberLevel(MemberLevelEnum.fromCode("  ")));
        assertThrows(BizException.class, () -> user.changeMemberLevel(MemberLevelEnum.fromCode("DIAMOND")));
        assertThrows(BizException.class, () -> user.changeMemberLevel(null));
    }

    // ==================== 标识换绑（PII 三字段） ====================

    @Test
    @DisplayName("changePhone — 合法手机号整体替换三字段；非法格式拒绝且原值不变")
    void shouldChangePhone_withThreeFieldReplacement() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        user.getPhone().getMask();

        user.changePhone("13900139000");

        assertNotNull(user.getPhone());
        assertEquals("13900139000", user.getPhone().getPlain(), "换绑后明文可用于解密核验");
        assertEquals("139****9000", user.getPhone().getMask());
        assertEquals(64, user.getPhone().getHash().length(), "HMAC-SHA256 盲索引 64 位十六进制");

        assertThrows(BizException.class, () -> user.changePhone("12345"));
        assertEquals("13900139000", user.getPhone().getPlain(), "非法输入不得破坏原值");
    }

    @Test
    @DisplayName("changeEmail — 归一化（小写去空格）后替换三字段；非法格式拒绝")
    void shouldChangeEmail_withNormalization() {
        User user = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertNull(user.getEmail(), "手机号注册用户初始无邮箱");

        user.changeEmail("  Buyer@Example.COM ");

        assertNotNull(user.getEmail());
        assertEquals("buyer@example.com", user.getEmail().getPlain());
        assertEquals(64, user.getEmail().getHash().length());

        assertThrows(BizException.class, () -> user.changeEmail("not-an-email"));
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
    @DisplayName("create — PHONE 落手机号三字段；EMAIL 落邮箱三字段；WECHAT 不落标识列（凭证归 user_auth）")
    void shouldCreateByIdentityType() {
        User phoneUser = User.create(IdentityTypeEnum.PHONE, "13800138000");
        assertEquals("13800138000", phoneUser.getPhone().getPlain());
        assertNull(phoneUser.getEmail());
        assertNotNull(phoneUser.getUserId(), "业务 UUID 由工厂生成");
        assertEquals(0, phoneUser.getBalance());

        User emailUser = User.create(IdentityTypeEnum.EMAIL, "buyer@example.com");
        assertEquals("buyer@example.com", emailUser.getEmail().getPlain());
        assertNull(emailUser.getPhone());

        User wechatUser = User.create(IdentityTypeEnum.WECHAT, "wx-openid-1");
        assertNull(wechatUser.getPhone(), "微信身份由 user_auth 承载，user 表不落列");
        assertNull(wechatUser.getEmail());
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
