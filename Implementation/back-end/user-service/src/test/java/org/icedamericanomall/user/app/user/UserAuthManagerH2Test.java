package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.ResetPasswordReqCO;
import org.icedamericanomall.dto.WechatWebLoginReqCO;
import org.icedamericanomall.user.domain.gateway.WechatOAuthGateway;
import org.icedamericanomall.user.domain.model.WechatProfile;
import org.icedamericanomall.user.domain.port.SmsCodeStore;
import org.icedamericanomall.user.domain.service.SmsCodeVerifier;
import org.icedamericanomall.user.app.points.PointsManager;
import org.icedamericanomall.user.infrastructure.adapter.PasswordVerifierAdapter;
import org.icedamericanomall.user.infrastructure.persistence.rbac.PermissionMapper;
import org.icedamericanomall.user.infrastructure.persistence.rbac.RoleMapper;
import org.icedamericanomall.user.infrastructure.persistence.rbac.RoleRepositoryImpl;
import org.icedamericanomall.user.infrastructure.persistence.user.UserDO;
import org.icedamericanomall.user.infrastructure.persistence.user.UserMapper;
import org.icedamericanomall.user.infrastructure.persistence.user.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证写操作 H2 集成测试（重构后版本）—— UserAuthManager + 真实仓储（H2）/ 端口替身。
 *
 * <p>验证找回密码（校验 + 密码重写）与微信登录（按 openid 查/建用户）链路；
 * 不使用 Spring 上下文，直接构建 H2 + MyBatis-Plus（含枚举处理器）。
 */
@DisplayName("UserAuthManager H2 集成测试")
class UserAuthManagerH2Test {

    private UserAuthManager userAuthManager;
    private UserMapper userMapper;
    private SmsCodeStore smsCodeStore;
    private WechatOAuthGateway wechatOAuthGateway;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
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
                    wx_openid VARCHAR(128), phone_hash VARCHAR(64),
                    register_time TIMESTAMP, last_login_time TIMESTAMP NULL, status INT DEFAULT 1, balance INT DEFAULT 0,
                    role_type INT DEFAULT 0, version INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, code VARCHAR(64))");
            stmt.execute("CREATE TABLE sys_user_role (user_id BIGINT, role_id BIGINT)");
            stmt.execute("CREATE TABLE sys_permission (id BIGINT PRIMARY KEY, code VARCHAR(64))");
            stmt.execute("CREATE TABLE sys_user_permission (user_id BIGINT, permission_id BIGINT)");
            stmt.execute("CREATE TABLE sys_role_permission (role_id BIGINT, permission_id BIGINT)");
            stmt.execute("INSERT INTO sys_role (id, code) VALUES (1, 'ROLE_USER')");
            stmt.execute("INSERT INTO user (user_id, username, phone, password, status, role_type) "
                    + "VALUES ('u-1', 'ice_u1', '13800138000', '" + passwordEncoder.encode("old_pw_1") + "', 1, 0)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
        config.addMapper(UserMapper.class);
        config.addMapper(RoleMapper.class);
        config.addMapper(PermissionMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        SqlSessionFactory factory = sqlSessionFactory;
        userMapper = factory.openSession().getMapper(UserMapper.class);
        RoleMapper roleMapper = factory.openSession().getMapper(RoleMapper.class);
        PermissionMapper permissionMapper = factory.openSession().getMapper(PermissionMapper.class);

        UserRepositoryImpl userRepository = new UserRepositoryImpl(userMapper);
        RoleRepositoryImpl roleRepository = new RoleRepositoryImpl(roleMapper, permissionMapper);
        UserRegistrar userRegistrar = new UserRegistrar(userRepository, roleRepository);

        smsCodeStore = mock(SmsCodeStore.class);
        when(smsCodeStore.read(org.mockito.ArgumentMatchers.anyString())).thenReturn("123456");
        wechatOAuthGateway = mock(WechatOAuthGateway.class);

        userAuthManager = new UserAuthManager(
                userRepository,
                roleRepository,
                userRegistrar,
                new PasswordVerifierAdapter(passwordEncoder),
                new SmsCodeVerifier(smsCodeStore),
                smsCodeStore,
                wechatOAuthGateway,
                mock(PointsManager.class));
    }

    private ResetPasswordReqCO req(String phone, String code, String newPw) {
        ResetPasswordReqCO r = new ResetPasswordReqCO();
        r.setPhone(phone);
        r.setCode(code);
        r.setNewPassword(newPw);
        return r;
    }

    private WechatWebLoginReqCO wxReq(String code) {
        WechatWebLoginReqCO r = new WechatWebLoginReqCO();
        r.setCode(code);
        r.setState("test-state");
        return r;
    }

    private UserDO findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhone, phone));
    }

    @Test
    @DisplayName("resetPassword — 验证码通过：密码被 BCrypt 重写，旧密码失效")
    void shouldResetPassword_whenCodeValid() {
        userAuthManager.resetPassword(req("13800138000", "123456", "new_pw_2"));

        UserDO user = findByPhone("13800138000");
        assertTrue(passwordEncoder.matches("new_pw_2", user.getPassword()), "新密码应生效");
        assertFalse(passwordEncoder.matches("old_pw_1", user.getPassword()), "旧密码应失效");
    }

    @Test
    @DisplayName("resetPassword — 手机号未注册：抛出异常")
    void shouldThrow_whenPhoneNotRegistered() {
        assertThrows(BizException.class,
                () -> userAuthManager.resetPassword(req("13900000000", "123456", "new_pw_2")));
    }

    @Test
    @DisplayName("loginByWechat — 新 openid：自动注册并写入 openid/昵称，默认角色已授予")
    void shouldAutoRegister_whenNewOpenid() {
        when(wechatOAuthGateway.getUserInfoByCode("wxcode-1"))
                .thenReturn(new WechatProfile("wxopenid-1", "小明", "http://x/wxopenid-1"));

        var resp = userAuthManager.loginByWechat(wxReq("wxcode-1"));

        assertNotNull(resp);
        assertEquals("小明", resp.getUsername());
        assertNotNull(resp.getRoles());
        assertTrue(resp.getRoles().contains("ROLE_USER"), "新用户应获得默认 ROLE_USER");

        UserDO user = userMapper.selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getWxOpenid, "wxopenid-1"));
        assertNotNull(user, "应自动注册微信用户");
        assertNull(user.getPhone(), "微信-only 用户无手机号");
        assertNull(user.getPassword(), "微信-only 用户无密码");
    }

    @Test
    @DisplayName("loginByWechat — 已存在 openid：直接登录不重复注册")
    void shouldLoginExisting_whenSameOpenid() {
        when(wechatOAuthGateway.getUserInfoByCode("wxcode-2"))
                .thenReturn(new WechatProfile("wxopenid-2", "小红", "http://x/wxopenid-2"));

        var first = userAuthManager.loginByWechat(wxReq("wxcode-2"));
        var second = userAuthManager.loginByWechat(wxReq("wxcode-2"));

        assertEquals(first.getUserId(), second.getUserId(), "同一 openid 应命中同一用户");
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getWxOpenid, "wxopenid-2"));
        assertEquals(1, count, "不应重复注册");
    }
}
