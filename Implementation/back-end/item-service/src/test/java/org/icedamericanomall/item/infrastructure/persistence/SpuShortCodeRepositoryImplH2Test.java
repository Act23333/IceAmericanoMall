package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.adapter.assembler.SkuConverter;
import org.icedamericanomall.item.adapter.assembler.SpuConverter;
import org.icedamericanomall.item.adapter.request.CreateSpuReq;
import org.icedamericanomall.item.app.FreightTemplateManager;
import org.icedamericanomall.item.app.SkuManager;
import org.icedamericanomall.item.app.SpuManager;
import org.icedamericanomall.item.app.SpuPromotionManager;
import org.icedamericanomall.item.app.SpuRecommendManager;
import org.icedamericanomall.item.app.kg.KgEdgeIngestManager;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.port.ShortCodeResolver;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code spu.short_code} 读写 H2 集成测试（ADR-063 §决策 8，批次 B7b）—— 在真 SQL 上锁定三件事：
 *
 * <ol>
 *   <li><b>等值查命中</b>：{@code SELECT p.* FROM spu p WHERE p.short_code = ?} 走唯一索引；</li>
 *   <li><b>不过滤 {@code status}</b>：软删（{@code status=3}）的行<b>仍能被短码查到</b> ——
 *       短码永不复用、删除态仍算「已占用」，状态判断属应用层（ADR-063 §决策 8.4）；</li>
 *   <li><b>唯一约束是硬约束</b>：重复 {@code short_code} 插入必须失败（发布时靠它做碰撞硬失败；
 *       重试逻辑在应用层，DB 不做静默覆盖）。</li>
 * </ol>
 *
 * <p><b>键集纪律（批次 U1）</b>：本表此前只有 {@code uk_short_code}，<b>丢了 {@code uk_spu_id}</b>
 * —— 于是 {@code SpuManager#saveWithShortCodeRetry} 的「业务键冲突<b>不得</b>重试」分支在 H2 上
 * 永远无法执行（重复 {@code spu_id} 在无键的表上直接插成功）。本批补键，并让 DB 抛出的
 * <b>真实</b>冲突喂回应用层，逐条证明「该重试 / 不该重试」两条分支都被真正走过。
 */
@DisplayName("spu.short_code H2 集成测试（ADR-063 §决策 8）")
class SpuShortCodeRepositoryImplH2Test {

    private static final String ON_SALE_CODE = "8f3a1c9d20";
    private static final String DELETED_CODE = "aaaaaaaaaa";

    private SpuRepositoryImpl repository;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:itemshortcode_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE spu (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    spu_id BINARY(16) NOT NULL,
                    short_code VARCHAR(16) NOT NULL,
                    seller_id BIGINT, category_id BIGINT, freight_template_id BIGINT,
                    name VARCHAR(100), main_image VARCHAR(255), images VARCHAR(2000),
                    video_url VARCHAR(500), description VARCHAR(255), brand VARCHAR(50),
                    sold_count INT DEFAULT 0, comment_count INT DEFAULT 0, is_ad INT DEFAULT 0,
                    status INT DEFAULT 1, publish_time TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_short_code UNIQUE (short_code),
                    CONSTRAINT uk_spu_id UNIQUE (spu_id)
                )""");
            stmt.execute("""
                INSERT INTO spu (id,spu_id,short_code,seller_id,category_id,name,status) VALUES
                (1, X'00000000000000000000000000000001', '""" + ON_SALE_CODE + "', 5, 10, '在售商品', 1),\n"
                    + "(2, X'00000000000000000000000000000002', '" + DELETED_CODE + "', 5, 10, '已删商品', 3)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SpuMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // 生产同款装配：SqlSessionTemplate 挂 Spring 异常翻译器（H2 23505 → DuplicateKeyException）。
        // 裸 openSession() 只能抛 MyBatis PersistenceException —— 冲突到不了应用层的 catch，
        // 「短码碰撞重试」与「业务键冲突不重试」两条分支都会被静默绕过。
        repository = new SpuRepositoryImpl(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(SpuMapper.class));
    }

    @Test
    @DisplayName("findByShortCode — 命中返回聚合（业务键出 hex、短码原样带回、状态如实）")
    void shouldResolveByShortCode() {
        var spu = repository.findByShortCode(ON_SALE_CODE);

        assertNotNull(spu);
        assertEquals("00000000000000000000000000000001", spu.getSpuId());
        assertEquals(ON_SALE_CODE, spu.getShortCode());
        assertEquals(1, spu.getStatus());
        assertEquals("在售商品", spu.getName());
    }

    @Test
    @DisplayName("findByShortCode — 软删（status=3）仍可查到：短码不回收，状态判断留给应用层")
    void shouldStillResolveDeletedRow() {
        var spu = repository.findByShortCode(DELETED_CODE);

        assertNotNull(spu, "短码永不复用 ⇒ 删除态必须仍能按码查到（否则「码还在、商品删了」会被误判成未分配）");
        assertEquals(3, spu.getStatus());
    }

    @Test
    @DisplayName("findByShortCode — 未分配 / 形态非法 → null（不放大成 500）")
    void shouldReturnNullWhenAbsentOrMalformed() {
        assertNull(repository.findByShortCode("0123456789"));
        assertNull(repository.findByShortCode("8F3A1C9D20"));
        assertNull(repository.findByShortCode("7"));
        assertNull(repository.findByShortCode(null));
    }

    @Test
    @DisplayName("uk_short_code — 重复短码插入必须失败（碰撞即硬失败，DB 不做静默覆盖）")
    void shouldRejectDuplicateShortCode() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            var ex = assertThrows(java.sql.SQLException.class, () -> stmt.execute(
                    "INSERT INTO spu (id,spu_id,short_code,seller_id,category_id,name,status) VALUES "
                            + "(3, X'00000000000000000000000000000003', '" + ON_SALE_CODE + "', 5, 10, '撞码商品', 1)"));
            assertTrue(String.valueOf(ex.getMessage()).toUpperCase().contains("UK_SHORT_CODE")
                            || String.valueOf(ex.getMessage()).toUpperCase().contains("UNIQUE"),
                    "冲突必须由唯一约束拦下: " + ex.getMessage());
        }
    }

    /**
     * <b>D3 承重用例 ①（本批新增）</b>：{@code uk_spu_id} 真实存在，且冲突消息指向<b>业务键</b>索引。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：{@code repository.save} 就是一条 INSERT
     * （{@code SpuRepositoryImpl#save} 无任何判重 if），能拦下「同 {@code spu_id}、不同
     * {@code short_code}」这次插入的只有 {@code uk_spu_id}。本断言在<b>无</b>该键的 H2 DDL 上会
     * <b>失败</b>：第二行会插进去（表里变 3 行），取证阶段直接抛 {@link AssertionError}
     * —— 变异验证 M3 即此处。
     */
    @Test
    @DisplayName("uk_spu_id — 同业务键、不同短码插入必须失败，且冲突索引名不是 uk_short_code")
    void shouldRejectDuplicateSpuId_notConfusedWithShortCode() {
        DuplicateKeyException e = duplicateKeyOn(
                candidateSpu("00000000000000000000000000000001", "ffffffffff"));

        assertTrue(e.getMessage().contains("uk_spu_id"),
                "冲突必须来自 uk_spu_id: " + e.getMessage());
        assertFalse(e.getMessage().contains("uk_short_code"),
                "业务键冲突不得被认成短码冲突（否则会套 3 次无意义重试）: " + e.getMessage());
        assertEquals(2L, rowCount(), "重复业务键不得落库");
    }

    /**
     * <b>D3 承重用例 ②（本批新增）</b>：{@code uk_spu_id} 冲突 → <b>不重试</b>
     * —— {@code SpuManager} 的「不该重试」分支被真正执行到。
     *
     * <p>喂给应用层的是上一条用例里 <b>DB 真实抛出</b>的那个异常对象（不是手写字符串）。
     * 若它被误判成短码冲突，{@code createSpu} 会换号重试共 3 次、并把
     * {@code DuplicateKeyException} 吞成 {@code BizException(INTERNAL_ERROR)}。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：分流判据就是 DB 错误消息里的索引名
     * （{@code uk_spu_id} vs {@code uk_short_code}）；无 {@code uk_spu_id} 时 DB 不抛，
     * 本用例在取证阶段即红。
     */
    @Test
    @DisplayName("createSpu — 冲突来自 uk_spu_id → 只试 1 次、原样抛出（不套 3 次无意义重试）")
    void shouldNotRetry_whenDuplicateKeyComesFromSpuId() {
        DuplicateKeyException dbError = duplicateKeyOn(
                candidateSpu("00000000000000000000000000000001", "ffffffffff"));

        SpuRepository stubRepository = mock(SpuRepository.class);
        when(stubRepository.save(any())).thenThrow(dbError);
        SpuManager manager = managerWith(stubRepository);

        DuplicateKeyException thrown = assertThrows(DuplicateKeyException.class,
                () -> manager.createSpu(5L, publishReq()),
                "业务键冲突属数据异常：原样抛出，不得重试后变成 500 文案");

        assertSame(dbError, thrown, "抛出的必须是 DB 那个异常对象（未被替换或包装）");
        verify(stubRepository, times(1)).save(any());
    }

    /**
     * <b>对照用例（本批新增）</b>：同一个 Manager、同一个库，<b>短码</b>冲突 → 换号重试 3 次后硬失败。
     *
     * <p>与上一条只差「冲突落在哪个索引」，因此「靠索引名分流」这件事本身也被测到了 ——
     * 两条用例必须一红一绿才会同时通过。
     */
    @Test
    @DisplayName("createSpu — 冲突来自 uk_short_code → 换号重试 3 次后 INTERNAL_ERROR（有界重试）")
    void shouldRetryAndFail_whenDuplicateKeyComesFromShortCode() {
        DuplicateKeyException dbError = duplicateKeyOn(
                candidateSpu("00000000000000000000000000000009", ON_SALE_CODE));

        SpuRepository stubRepository = mock(SpuRepository.class);
        when(stubRepository.save(any())).thenThrow(dbError);
        SpuManager manager = managerWith(stubRepository);

        BizException ex = assertThrows(BizException.class, () -> manager.createSpu(5L, publishReq()));
        assertEquals(ErrorCode.INTERNAL_ERROR, ex.getErrorCode(), "重试耗尽 → 硬失败（不回落到派生值）");
        verify(stubRepository, times(3)).save(any());
    }

    /**
     * <b>键集断言（本批新增）</b>：{@code spu} 表唯一键集与 {@code Initialize.sql:297-299} 一致
     * （{@code uk_spu_id} + {@code uk_short_code}，列名/列序不差）。缺一即红。
     */
    @Test
    @DisplayName("DDL 键集 — spu 的 uk_spu_id / uk_short_code 与 Initialize.sql 一致")
    void shouldExposeProductionUniqueKeySet() {
        Map<String, List<String>> keys = uniqueKeysOf("spu");

        assertEquals(Set.of("uk_spu_id", "uk_short_code"), keys.keySet(), "两个唯一键一个都不能少");
        assertEquals(List.of("spu_id"), keys.get("uk_spu_id"));
        assertEquals(List.of("short_code"), keys.get("uk_short_code"));
    }

    /** 用真实仓储 + 真实 DB 约束造一次冲突，返回<b>生产同款</b>（Spring 已翻译）的 {@link DuplicateKeyException}。 */
    private DuplicateKeyException duplicateKeyOn(Spu candidate) {
        try {
            repository.save(candidate);
        } catch (DuplicateKeyException e) {
            return e;
        }
        throw new AssertionError("DB 未拦下这次插入 —— 唯一键缺失，本用例失去意义（这就是改前的假绿）");
    }

    /** 构造一条「未落库」的 spu（业务键与短码可控，用于制造指定索引上的冲突）。 */
    private static Spu candidateSpu(String spuId, String shortCode) {
        return Spu.builder()
                .spuId(spuId).shortCode(shortCode).sellerId(5L).categoryId(10L)
                .name("撞键商品").soldCount(0).commentCount(0).isAd(0).status(1)
                .build();
    }

    /** 只关心 {@code saveWithShortCodeRetry} 的 SpuManager：其余协作者全部替身，且在本用例不可达。 */
    private static SpuManager managerWith(SpuRepository spuRepository) {
        return new SpuManager(spuRepository, mock(SkuRepository.class), mock(SpuConverter.class),
                mock(SkuConverter.class), mock(FreightTemplateManager.class), mock(SkuManager.class),
                mock(org.icedamericanomall.client.SpuVectorClient.class), mock(SpuRecommendManager.class),
                mock(SpuPromotionManager.class), mock(KgEdgeIngestManager.class), mock(ShortCodeResolver.class));
    }

    private static CreateSpuReq publishReq() {
        CreateSpuReq req = new CreateSpuReq();
        req.setCategoryId(10L);
        req.setSpuName("撞键商品");
        req.setSkus(List.of());
        return req;
    }

    private long rowCount() {
        Long count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM spu", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 该表的唯一键 → 有序列集（列顺序取自 {@code KEY_COLUMN_USAGE.ORDINAL_POSITION}）。
     *
     * @param table 表名（本测试 DATABASE_TO_LOWER=TRUE，传小写）
     * @return 键名 → 列名列表
     */
    private Map<String, List<String>> uniqueKeysOf(String table) {
        Map<String, List<String>> keys = new LinkedHashMap<>();
        new JdbcTemplate(dataSource).query(
                "SELECT c.constraint_name AS name, k.column_name AS col "
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
}
