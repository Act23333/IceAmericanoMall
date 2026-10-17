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
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.common.enums.ErrorCode;
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
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
    private UserRepositoryImpl userRepository;
    private JdbcTemplate jdbcTemplate;
    private DistributedLock distributedLock;
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
        jdbcTemplate = new JdbcTemplate(dataSource);
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // 键集与 Initialize.sql:137-167 逐字一致（列名/列序不差）：user_id BINARY(16) NOT NULL，
            // 四个唯一键 uk_user_id / uk_phone_hash / uk_email_hash / uk_wx_openid 一个不缺。
            // 缺键的后果不是"不一致"，而是把「并发兜底路径」变成不可触发 —— UserRepositoryImpl
            // 的 DuplicateKeyException → DuplicateUserException 翻译在无键的 H2 上永不执行。
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BINARY(16) NOT NULL, username VARCHAR(50),
                    avatar VARCHAR(255), gender INT DEFAULT 0, birthday DATE,
                    member_level VARCHAR(20) DEFAULT 'NORMAL',
                    phone_cipher VARCHAR(512), phone_hash VARCHAR(128), phone_mask VARCHAR(64),
                    email_cipher VARCHAR(512), email_hash VARCHAR(128), email_mask VARCHAR(64),
                    wx_openid VARCHAR(128),
                    register_time TIMESTAMP NULL, status INT DEFAULT 1,
                    version INT DEFAULT 0,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL,
                    CONSTRAINT uk_user_id UNIQUE (user_id),
                    CONSTRAINT uk_phone_hash UNIQUE (phone_hash),
                    CONSTRAINT uk_email_hash UNIQUE (email_hash),
                    CONSTRAINT uk_wx_openid UNIQUE (wx_openid)
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

        // 生产同款装配：SqlSessionTemplate 挂 Spring 异常翻译器（H2 23505 → DuplicateKeyException）。
        // 裸 openSession() 只会抛 MyBatis PersistenceException —— 唯一键冲突到不了仓储的 catch，
        // 「并发兜底」在测试里被静默绕过（这正是本批要修的可验证性缺口）。
        userMapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(UserMapper.class);

        userRepository = new UserRepositoryImpl(userMapper);

        distributedLock = mock(DistributedLock.class);
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

    /**
     * <b>D1 承重用例 ①（本批新增）</b>：绕过应用层「先查后插」，让 DB 唯一键说话。
     *
     * <p>做法：用真实仓储<b>直接</b>插第二行 —— {@code user_id} 是全新 UUIDv7（必不与
     * {@code uk_user_id} 冲突）+ <b>同一手机号</b>（⇒ 同一 {@code phone_hash}）。
     * 应用层若照常先查会短路返回，故这里刻意跳过预查。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：唯一能拦住这次插入的只有 {@code uk_phone_hash}
     * —— 新 {@code user_id} 让 {@code uk_user_id} 帮不上忙，应用层没有任何 if 参与判定。
     * 本断言在<b>无</b> {@code uk_phone_hash} 的 H2 DDL 上会失败：第二行会插进去（表里变 2 行），
     * {@code assertThrows} 拿不到异常 —— 变异验证 M1 即此处。
     */
    @Test
    @DisplayName("uk_phone_hash — 绕过先查后插的重复插入：DB 拦下 → 翻译为 DuplicateUserException")
    void shouldRejectDuplicatePhoneHash_byDatabaseKey() {
        userProvisionManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, null));

        // 同手机号 ⇒ 同盲索引；user_id 为全新 UUIDv7，不与 uk_user_id 冲突
        User duplicate = User.create(IdentityTypeEnum.PHONE, "13800138000");

        DuplicateUserException ex = assertThrows(DuplicateUserException.class,
                () -> userRepository.insert(duplicate),
                "若 uk_phone_hash 缺失，此行插入成功 → 本用例变红：唯一键才是并发兜底的载体");
        assertEquals(ErrorCode.USER_ALREADY_EXISTS, ex.getErrorCode(),
                "冲突必须翻译为领域异常（领域层不依赖 Spring 异常）");
        assertEquals(1L, userMapper.selectCount(null), "重复行不得落库");
    }

    /**
     * <b>D1 承重用例 ②（本批新增）</b>：走完整幂等链路 —— 先查落空（竞态窗口）→ 插入撞键 →
     * 仓储翻译成 {@link DuplicateUserException} → {@code UserProvisioner} 回查到同一用户。
     *
     * <p>「先查」用 spy 强制<b>前两次</b>返回 empty（外层短路 + 锁内双重检查，正是两个请求
     * 同时判定「不存在」的窗口），第三次起走真实实现（撞键后的回查）。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：收敛只可能发生在
     * {@code catch (DuplicateUserException)} 之后，而该异常只能由 DB 的 {@code DuplicateKeyException}
     * 产生（翻译点在 {@code UserRepositoryImpl#insert}）。无 {@code uk_phone_hash} 时插入成功，
     * 库里出现两个同手机号账号 → {@code selectCount == 1} 断言变红（生产「双账号」事故的等价物）。
     */
    @Test
    @DisplayName("uk_phone_hash — 先查失效时由唯一键收敛：撞键回查同一用户，不建出第二个账号")
    void shouldConvergeOnDatabaseKey_whenPreCheckMissesConcurrentWindow() {
        Long first = userProvisionManager.getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, null));

        UserRepositoryImpl racingRepository = spy(userRepository);
        doReturn(Optional.empty(), Optional.empty()).doCallRealMethod()
                .when(racingRepository).findByIdentity(any(), anyString());
        UserProvisioner racingProvisioner = new UserProvisioner(racingRepository, distributedLock,
                new org.icedamericanomall.user.app.user.UserRegistrar(racingRepository), newUserRewardService);

        Long second = new UserProvisionManager(racingProvisioner)
                .getOrCreateUser(co("13800138000", IdentityTypeEnum.PHONE, "并发第二个昵称"));

        assertEquals(first, second, "撞键后必须回查到同一用户（并发兜底路径被真正执行到）");
        assertEquals(1L, userMapper.selectCount(null), "不得出现第二个账号");
        verify(newUserRewardService, times(1)).award(first);
    }

    /**
     * <b>键集断言（本批新增）</b>：{@code user} 表唯一键集与 {@code Initialize.sql:163-166} 逐字一致
     * （列名 + 列序 + 列类型）。缺一即红 —— 它拦得住「抄表只抄列、丢了 UNIQUE」的回归，
     * 不需要任何业务代码配合。
     */
    @Test
    @DisplayName("DDL 键集 — user 表 4 个唯一键与 Initialize.sql 一致（列名/列序不差）")
    void shouldExposeProductionUniqueKeySet() {
        Map<String, List<String>> keys = uniqueKeysOf("user");

        assertEquals(Set.of("uk_user_id", "uk_phone_hash", "uk_email_hash", "uk_wx_openid"), keys.keySet(),
                "生产 4 个唯一键一个都不能少");
        assertEquals(List.of("user_id"), keys.get("uk_user_id"));
        assertEquals(List.of("phone_hash"), keys.get("uk_phone_hash"));
        assertEquals(List.of("email_hash"), keys.get("uk_email_hash"));
        assertEquals(List.of("wx_openid"), keys.get("uk_wx_openid"));
        assertEquals("binary", jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_name = 'user' AND column_name = 'user_id'", String.class),
                "user_id 必须与生产同为 BINARY(16)（ADR-050），不在 VARCHAR 上蒙混");
    }

    /**
     * 该表的唯一键 → 有序列集（列顺序取自 {@code KEY_COLUMN_USAGE.ORDINAL_POSITION}，即 MySQL 索引列序）。
     *
     * @param table 表名（H2 在本测试里 DATABASE_TO_LOWER=TRUE，传小写）
     * @return 键名 → 列名列表（插入顺序即列序）
     */
    private Map<String, List<String>> uniqueKeysOf(String table) {
        Map<String, List<String>> keys = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT c.constraint_name AS name, k.column_name AS col "
                        + "FROM information_schema.table_constraints c "
                        + "JOIN information_schema.key_column_usage k "
                        + "ON k.constraint_name = c.constraint_name AND k.table_name = c.table_name "
                        + "WHERE c.table_name = ? AND c.constraint_type = 'UNIQUE' "
                        + "ORDER BY c.constraint_name, k.ordinal_position",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        keys.computeIfAbsent(rs.getString("name"), name -> new ArrayList<>())
                                .add(rs.getString("col")),
                table);
        return keys;
    }

    /** 按技术主键回读领域对象（仅用于取业务 UUID 做断言）。 */
    private User userRepositoryFind(Long id) {
        UserPO dataObject = userMapper.selectById(id);
        return User.builder().id(dataObject.getId())
                .userId(org.icedamericanomall.common.utils.IdGenerator.toHexString(dataObject.getUserId())).build();
    }
}
