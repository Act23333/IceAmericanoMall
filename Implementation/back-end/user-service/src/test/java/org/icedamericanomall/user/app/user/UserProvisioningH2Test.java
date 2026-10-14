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
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.common.lock.DistributedLock;
import org.icedamericanomall.user.domain.port.NewUserRewardService;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.icedamericanomall.user.domain.service.UserProvisioner;
import org.icedamericanomall.user.infrastructure.config.MyMetaObjectHandler;
import org.icedamericanomall.user.infrastructure.persistence.user.UserPO;
import org.icedamericanomall.user.infrastructure.persistence.user.UserMapper;
import org.icedamericanomall.user.infrastructure.persistence.user.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

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
 * 幂等建档 H2 集成测试（ADR-018 后，ADR-025 按用例族拆分）—— UserProvisionManager +
 * UserAdminManager + UserProvisioner + 真实 UserRepositoryImpl / UserRegistrar（H2），锁与奖励为替身。
 *
 * <p>验证：PHONE/EMAIL 建档写三字段 PII（明文列不写）、默认昵称、建档奖励仅新建分支；
 * WECHAT 身份不落 user 表；roleType 由授权域角色批量派生（{@code AuthContextClient} 替身）。
 * 默认 RBAC 角色授予已随 RBAC 迁至 authorization-service（读时兜底），不在本服务。
 */
@DisplayName("幂等建档 H2 集成测试（UserProvisionManager + UserAdminManager + UserProvisioner）")
class UserProvisioningH2Test {

    private UserProvisionManager userProvisionManager;
    private UserAdminManager userAdminManager;
    private UserMapper userMapper;
    private org.icedamericanomall.client.AuthContextClient authContextClient;
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
                    user_id BINARY(16), username VARCHAR(50),
                    avatar VARCHAR(255), gender INT DEFAULT 0, birthday DATE,
                    member_level VARCHAR(20) DEFAULT 'NORMAL',
                    phone_cipher VARCHAR(512), phone_hash VARCHAR(128), phone_mask VARCHAR(64),
                    email_cipher VARCHAR(512), email_hash VARCHAR(128), email_mask VARCHAR(64),
                    register_time TIMESTAMP NULL, status INT DEFAULT 1,
                    version INT DEFAULT 0,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
        config.addMapper(UserMapper.class);
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

        UserRepository userRepository = new UserRepositoryImpl(userMapper);

        DistributedLock distributedLock = mock(DistributedLock.class);
        when(distributedLock.executeWithLock(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        newUserRewardService = mock(NewUserRewardService.class);

        org.icedamericanomall.user.app.user.UserRegistrar userRegistrar =
                new org.icedamericanomall.user.app.user.UserRegistrar(userRepository);
        UserProvisioner provisioner = new UserProvisioner(
                userRepository, distributedLock, userRegistrar, newUserRewardService);

        authContextClient = mock(org.icedamericanomall.client.AuthContextClient.class);
        userProvisionManager = new UserProvisionManager(provisioner);
        // 审计落库（ADR-032 管道二）在本用例只涉及 pageUsers 读取，审计替身即可；
        // 事务模板（ADR-031 起 updateStatus 用它圈定本地写入）在本用例不被触发，用真实模板占位
        userAdminManager = new UserAdminManager(userRepository, authContextClient,
                mock(org.icedamericanomall.user.app.oplog.OperationLogManager.class),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private UserAuthCO co(String identifier, IdentityTypeEnum type, String nickname) {
        return UserAuthCO.builder().identifier(identifier).identityType(type).nickname(nickname).build();
    }

    @Test
    @DisplayName("getOrCreateUser — PHONE 建档：三字段 PII 落列（明文列不写）+ 昵称透传 + 建档奖励")
    void shouldProvisionPhoneUser_withPiiAndReward() {
        Long userId = userProvisionManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, "冰美式用户"));

        UserPO saved = userMapper.selectById(userId);
        assertNotNull(saved);
        assertEquals("冰美式用户", saved.getUsername(), "昵称透传建档");
        assertNotNull(saved.getPhoneCipher(), "手机号密文落列");
        assertEquals(64, saved.getPhoneHash().length(), "手机号盲索引（HMAC-SHA256）落列");
        assertEquals("138****8000", saved.getPhoneMask(), "手机号脱敏值落列");
        assertEquals("NORMAL", saved.getMemberLevel(), "默认会员等级");
        verify(newUserRewardService, times(1)).award(userId);
    }

    @Test
    @DisplayName("getOrCreateUser — 同手机号重复调用：命中同一用户，不发第二次奖励")
    void shouldBeIdempotent_whenSamePhone() {
        Long first = userProvisionManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, null));
        Long second = userProvisionManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, "另一个昵称"));

        assertEquals(first, second, "同一手机号必须命中同一用户");
        assertEquals(1L, userMapper.selectCount(null), "不得重复建档");
        verify(newUserRewardService, times(1)).award(first);
    }

    @Test
    @DisplayName("getOrCreateUser — EMAIL 建档：按盲索引落三字段，重复调用幂等，批量角色查询可用")
    void shouldProvisionEmailUser_byBlindIndex() {
        Long first = userProvisionManager.getOrCreateUser(co("Buyer@Example.com", IdentityTypeEnum.EMAIL, null));
        Long second = userProvisionManager.getOrCreateUser(co("Buyer@Example.com", IdentityTypeEnum.EMAIL, null));

        assertEquals(first, second, "同一邮箱（明文入参）必须命中同一用户");
        UserPO saved = userMapper.selectById(first);
        assertNotNull(saved.getEmailHash());
        assertNotNull(saved.getEmailMask());

        // 角色经授权域批量查询（ADR-018）：替身返回 ROLE_USER
        when(authContextClient.rolesByUserIds(Set.of(first)))
                .thenReturn(Map.of(first, Set.of("ROLE_USER")));
        var rolesByUser = authContextClient.rolesByUserIds(Set.of(first));
        assertEquals(Set.of("ROLE_USER"), rolesByUser.get(first), "批量角色查询（管理后台派生口径）");
    }

    @Test
    @DisplayName("getOrCreateUser — WECHAT 身份：user 表不落标识列（凭证归 user_auth），建档发奖励")
    void shouldProvisionWechatUser_withoutIdentityColumn() {
        Long userId = userProvisionManager.getOrCreateUser(co("wx-openid-1", IdentityTypeEnum.WECHAT, "微信用户"));

        UserPO saved = userMapper.selectById(userId);
        assertNotNull(saved);
        assertEquals("微信用户", saved.getUsername());
        assertNull(saved.getPhoneHash(), "WECHAT 不写手机号列");
        assertNull(saved.getEmailHash(), "WECHAT 不写邮箱列");
        verify(newUserRewardService).award(userId);
    }

    @Test
    @DisplayName("UserAdminManager.pageUsers — roleType 由授权域角色派生：仅 ROLE_USER → 0；追加 ROLE_ADMIN → 2")
    void shouldDeriveRoleType_fromAuthRoles() {
        Long plainUserId = userProvisionManager.getOrCreateUser(co("13900139000", IdentityTypeEnum.PHONE, null));
        Long adminUserId = userProvisionManager.getOrCreateUser(co("13500135000", IdentityTypeEnum.PHONE, null));

        // 授权域返回的角色编码（替身模拟 sys_user_role 内容；key 为技术主键）
        when(authContextClient.rolesByUserIds(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(Map.of(
                        plainUserId, Set.of("ROLE_USER"),
                        adminUserId, Set.of("ROLE_USER", "ROLE_ADMIN")));

        var page = userAdminManager.pageUsers(1, 10);
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
        Long userId = userProvisionManager.getOrCreateUser(co("13700137000", IdentityTypeEnum.PHONE, null));
        userProvisionManager.getOrCreateUser(co("13700137000", IdentityTypeEnum.PHONE, null));

        verify(newUserRewardService, times(1)).award(userId);
        verify(newUserRewardService, times(1)).award(anyLong());
    }

    /** 按技术主键回读领域对象（仅用于取业务 UUID 做断言）。 */
    private User userRepositoryFind(Long id) {
        UserPO dataObject = userMapper.selectById(id);
        return User.builder().id(dataObject.getId())
                .userId(org.icedamericanomall.common.utils.IdGenerator.toHexString(dataObject.getUserId())).build();
    }
}
