package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.dto.ResetPasswordReqDTO;
import org.icedamericanomall.mapper.UserMapper;
import org.icedamericanomall.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * H2 集成测试 —— 验证 AuthServiceImpl.resetPassword（找回密码）的短信校验 + 密码重写链路。
 * 不使用 Spring 上下文，直接构建 H2 + MyBatis-Plus（含枚举处理器）。
 */
@DisplayName("AuthServiceImpl 找回密码 H2 集成测试")
class AuthServiceImplH2Test {

    private AuthServiceImpl authService;
    private SmsService smsService;
    private org.icedamericanomall.integration.wechat.WechatOAuthClient wechatOAuthClient;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:authreset_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(64), username VARCHAR(50), phone VARCHAR(20),
                    password VARCHAR(100), avatar VARCHAR(255),
                    wx_openid VARCHAR(128),
                    register_time TIMESTAMP, last_login_time TIMESTAMP NULL, status INT DEFAULT 1, balance INT DEFAULT 0,
                    role_type INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("INSERT INTO user (user_id, username, phone, password, status, role_type) " +
                    "VALUES ('u-1', 'ice_u1', '13800138000', '" + passwordEncoder.encode("old_pw_1") + "', 1, 0)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
        config.addMapper(UserMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        smsService = mock(SmsService.class);
        wechatOAuthClient = mock(org.icedamericanomall.integration.wechat.WechatOAuthClient.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        org.icedamericanomall.service.PointsService pointsSvc =
                mock(org.icedamericanomall.service.PointsService.class);
        var roleMapper = mock(org.icedamericanomall.mapper.RoleMapper.class);
        var permissionMapper = mock(org.icedamericanomall.mapper.PermissionMapper.class);
        authService = new AuthServiceImpl(smsService, redisTemplate, passwordEncoder, wechatOAuthClient,
                pointsSvc, roleMapper, permissionMapper);
        ReflectionTestUtils.setField(authService, "baseMapper",
                sqlSessionFactory.openSession().getMapper(UserMapper.class));
    }

    private ResetPasswordReqDTO req(String phone, String code, String newPw) {
        ResetPasswordReqDTO r = new ResetPasswordReqDTO();
        r.setPhone(phone); r.setCode(code); r.setNewPassword(newPw);
        return r;
    }

    @Test
    @DisplayName("resetPassword — 验证码通过：密码被 BCrypt 重写，旧密码失效")
    void shouldResetPassword_whenCodeValid() {
        doNothing().when(smsService).verifyCode(eq("13800138000"), anyString());

        authService.resetPassword(req("13800138000", "123456", "new_pw_2"));

        UserEntity user = authService.lambdaQuery().eq(UserEntity::getPhone, "13800138000").one();
        assertTrue(passwordEncoder.matches("new_pw_2", user.getPassword()), "新密码应生效");
        assertFalse(passwordEncoder.matches("old_pw_1", user.getPassword()), "旧密码应失效");
        verify(smsService).verifyCode(eq("13800138000"), eq("123456"));
    }

    @Test
    @DisplayName("resetPassword — 手机号未注册：抛出异常")
    void shouldThrow_whenPhoneNotRegistered() {
        doNothing().when(smsService).verifyCode(anyString(), anyString());
        assertThrows(RuntimeException.class,
                () -> authService.resetPassword(req("13900000000", "123456", "new_pw_2")));
    }

    private org.icedamericanomall.integration.wechat.WechatUserInfo wxUser(String openid, String nickname) {
        var u = new org.icedamericanomall.integration.wechat.WechatUserInfo();
        u.setOpenid(openid); u.setNickname(nickname); u.setAvatarUrl("http://x/" + openid);
        return u;
    }

    private org.icedamericanomall.dto.WechatLoginReqDTO wxReq(String code) {
        var r = new org.icedamericanomall.dto.WechatLoginReqDTO();
        r.setCode(code);
        return r;
    }

    @Test
    @DisplayName("loginByWechat — 新 openid：自动注册并写入 openid/昵称")
    void shouldAutoRegister_whenNewOpenid() {
        when(wechatOAuthClient.getUserInfoByCode("wxcode-1")).thenReturn(wxUser("wxopenid-1", "小明"));

        var resp = authService.loginByWechat(wxReq("wxcode-1"));

        assertNotNull(resp);
        assertEquals("小明", resp.getUsername());
        UserEntity user = authService.lambdaQuery().eq(UserEntity::getWxOpenid, "wxopenid-1").one();
        assertNotNull(user, "应自动注册微信用户");
        assertNull(user.getPhone(), "微信-only 用户无手机号");
        assertNull(user.getPassword(), "微信-only 用户无密码");
    }

    @Test
    @DisplayName("loginByWechat — 已存在 openid：直接登录不重复注册")
    void shouldLoginExisting_whenSameOpenid() {
        when(wechatOAuthClient.getUserInfoByCode("wxcode-2")).thenReturn(wxUser("wxopenid-2", "小红"));

        var first = authService.loginByWechat(wxReq("wxcode-2"));
        var second = authService.loginByWechat(wxReq("wxcode-2"));

        assertEquals(first.getUserId(), second.getUserId(), "同一 openid 应命中同一用户");
        long count = authService.lambdaQuery().eq(UserEntity::getWxOpenid, "wxopenid-2").count();
        assertEquals(1, count, "不应重复注册");
    }
}
