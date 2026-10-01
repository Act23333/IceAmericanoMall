package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.user.app.rbac.RoleTypeDerivation;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.port.DistributedLock;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.icedamericanomall.user.infrastructure.config.MyMetaObjectHandler;
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

import javax.sql.DataSource;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 幂等建档 H2 集成测试（V5.2 资料/凭证分离后）—— UserManager + UserProvisioner +
 * 真实 UserRepositoryImpl / UserRegistrar / RoleRepository（H2），锁与奖励为替身。
 *
 * <p>验证：PHONE/EMAIL 建档写三字段 PII（明文列不写）、默认昵称/角色、建档奖励仅新建分支；
 * WECHAT 身份不落 user 表（幂等由调用方 user_auth 保证）；roleType 由五表角色批量派生。
 */
@DisplayName("幂等建档 H2 集成测试（UserManager + UserProvisioner）")
class UserProvisioningH2Test {

    private UserManager userManager;
    private UserMapper userMapper;
    private RoleMapper roleMapper;
    private RoleRepository roleRepository;
    private NewUserRewardService newUserRewardService;

    @BeforeEach
    void setUp() throws Exception {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));

        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:provision_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(64), username VARCHAR(50),
                    avatar VARCHAR(255), gender INT DEFAULT 0, birthday DATE,
                    member_level VARCHAR(20) DEFAULT 'NORMAL',
                    phone_cipher VARCHAR(512), phone_hash VARCHAR(128), phone_mask VARCHAR(64),
                    email_cipher VARCHAR(512), email_hash VARCHAR(128), email_mask VARCHAR(64),
                    register_time TIMESTAMP NULL, status INT DEFAULT 1, balance INT DEFAULT 0,
                    version INT DEFAULT 0,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL
                )""");
            stmt.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, code VARCHAR(64))");
            stmt.execute("CREATE TABLE sys_user_role (user_id BIGINT, role_id BIGINT)");
            stmt.execute("CREATE TABLE sys_permission (id BIGINT PRIMARY KEY, code VARCHAR(128))");
            stmt.execute("CREATE TABLE sys_user_permission (user_id BIGINT, permission_id BIGINT)");
            stmt.execute("CREATE TABLE sys_role_permission (role_id BIGINT, permission_id BIGINT)");
            stmt.execute("INSERT INTO sys_role (id, code) VALUES (1, 'ROLE_USER'), (4, 'ROLE_ADMIN')");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
        config.addMapper(UserMapper.class);
        config.addMapper(RoleMapper.class);
        config.addMapper(PermissionMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        globalConfig.setMetaObjectHandler(new MyMetaObjectHandler());
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        SqlSessionFactory factory = sqlSessionFactory;
        userMapper = factory.openSession().getMapper(UserMapper.class);
        roleMapper = factory.openSession().getMapper(RoleMapper.class);
        PermissionMapper permissionMapper = factory.openSession().getMapper(PermissionMapper.class);

        UserRepository userRepository = new UserRepositoryImpl(userMapper);
        roleRepository = new RoleRepositoryImpl(roleMapper, permissionMapper);

        DistributedLock distributedLock = mock(DistributedLock.class);
        when(distributedLock.executeWithLock(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        newUserRewardService = mock(NewUserRewardService.class);

        UserRegistrar userRegistrar = new UserRegistrar(userRepository, roleRepository);
        UserProvisioner provisioner = new UserProvisioner(
                userRepository, distributedLock, userRegistrar, newUserRewardService);
        userManager = new UserManager(userRepository, provisioner, roleRepository);
    }

    private UserAuthCO co(String identifier, IdentityTypeEnum type, String nickname) {
        return UserAuthCO.builder().identifier(identifier).identityType(type).nickname(nickname).build();
    }

    @Test
    @DisplayName("getOrCreateUser — PHONE 建档：三字段 PII 落列（明文列不写）+ 昵称透传 + 默认角色 + 建档奖励")
    void shouldProvisionPhoneUser_withPiiAndReward() {
        Long userId = userManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, "冰美式用户"));

        UserDO saved = userMapper.selectById(userId);
        assertNotNull(saved);
        assertEquals("冰美式用户", saved.getUsername(), "昵称透传建档");
        assertNotNull(saved.getPhoneCipher(), "手机号密文落列");
        assertEquals(64, saved.getPhoneHash().length(), "手机号盲索引（HMAC-SHA256）落列");
        assertEquals("138****8000", saved.getPhoneMask(), "手机号脱敏值落列");
        assertEquals("NORMAL", saved.getMemberLevel(), "默认会员等级");
        // 说明：register_time（NOT NULL 列）由 MyMetaObjectHandler 在 Spring 上下文内填充，
        // 本测试为手工 MyBatis-Plus 引导（不启容器），故不对填充值断言。

        Long roleRows = userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getId, userId));
        assertEquals(1L, roleRows, "用户行唯一");
        assertEquals(Set.of("ROLE_USER"), roleRepository.findRoleCodesByUserId(userId), "默认 ROLE_USER 已授予");
        verify(newUserRewardService, times(1)).award(userId);
    }

    @Test
    @DisplayName("getOrCreateUser — 同手机号重复调用：命中同一用户，不发第二次奖励")
    void shouldBeIdempotent_whenSamePhone() {
        Long first = userManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, null));
        Long second = userManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, "另一个昵称"));

        assertEquals(first, second, "同一手机号必须命中同一用户");
        assertEquals(1L, userMapper.selectCount(null), "不得重复建档");
        verify(newUserRewardService, times(1)).award(first);
    }

    @Test
    @DisplayName("getOrCreateUser — EMAIL 建档：按盲索引落三字段，重复调用幂等，批量角色查询可用")
    void shouldProvisionEmailUser_byBlindIndex() {
        Long first = userManager.getOrCreateUser(co("Buyer@Example.com", IdentityTypeEnum.EMAIL, null));
        Long second = userManager.getOrCreateUser(co("Buyer@Example.com", IdentityTypeEnum.EMAIL, null));

        assertEquals(first, second, "同一邮箱（明文入参）必须命中同一用户");
        UserDO saved = userMapper.selectById(first);
        assertNotNull(saved.getEmailHash());
        assertNotNull(saved.getEmailMask());

        Map<Long, Set<String>> rolesByUser = roleRepository.findRoleCodesByUserIds(Set.of(first));
        assertEquals(Set.of("ROLE_USER"), rolesByUser.get(first), "批量角色查询（管理后台派生口径）");
    }

    @Test
    @DisplayName("getOrCreateUser — WECHAT 身份：user 表不落标识列（凭证归 user_auth），建档发奖励")
    void shouldProvisionWechatUser_withoutIdentityColumn() {
        Long userId = userManager.getOrCreateUser(co("wx-openid-1", IdentityTypeEnum.WECHAT, "微信用户"));

        UserDO saved = userMapper.selectById(userId);
        assertNotNull(saved);
        assertEquals("微信用户", saved.getUsername());
        assertNull(saved.getPhoneHash(), "WECHAT 不写手机号列");
        assertNull(saved.getEmailHash(), "WECHAT 不写邮箱列");
        verify(newUserRewardService).award(userId);
    }

    @Test
    @DisplayName("pageUsers — roleType 由五表派生：仅 ROLE_USER → 0；追加 ROLE_ADMIN → 2")
    void shouldDeriveRoleType_fromFiveTables() {
        Long plainUserId = userManager.getOrCreateUser(co("13900139000", IdentityTypeEnum.PHONE, null));
        Long adminUserId = userManager.getOrCreateUser(co("13500135000", IdentityTypeEnum.PHONE, null));
        roleMapper.insertUserRole(adminUserId, 4L); // 追加 ROLE_ADMIN

        var page = userManager.pageUsers(1, 10);
        Map<String, Integer> roleTypeByUserId = page.getRecords().stream()
                .collect(java.util.stream.Collectors.toMap(UserInfoResp::getUserId, UserInfoResp::getRoleType));

        User plainUser = userRepositoryFind(plainUserId);
        User adminUser = userRepositoryFind(adminUserId);
        assertEquals(RoleTypeDerivation.ROLE_TYPE_USER, roleTypeByUserId.get(plainUser.getUserId()));
        assertEquals(RoleTypeDerivation.ROLE_TYPE_ADMIN, roleTypeByUserId.get(adminUser.getUserId()));
    }

    @Test
    @DisplayName("getOrCreateUser — 奖励仅新建分支触发（重复调用不叠加）")
    void shouldAwardOnlyOnCreation() {
        Long userId = userManager.getOrCreateUser(co("13700137000", IdentityTypeEnum.PHONE, null));
        userManager.getOrCreateUser(co("13700137000", IdentityTypeEnum.PHONE, null));

        verify(newUserRewardService, times(1)).award(userId);
        verify(newUserRewardService, times(1)).award(anyLong());
    }

    /** 按技术主键回读领域对象（仅用于取业务 UUID 做断言）。 */
    private User userRepositoryFind(Long id) {
        UserDO dataObject = userMapper.selectById(id);
        return User.builder().id(dataObject.getId()).userId(dataObject.getUserId()).build();
    }
}
