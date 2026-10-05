package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 集成测试 — 验证 {@code InvoiceTitleRepositoryImpl} 持久化语义（INV6-9，契约 §4.15 V5.16.1）：
 * {@code uk_user_default} 多 NULL 唯一不变量（一用户至多一条默认）、{@code updateFull} 显式 SQL
 * 的 NULL 清列语义（企业转个人清税号 / 取消默认）、软删（@TableLogic）过滤与归属双条件。
 * 不使用 Spring Boot 上下文，直接构建 H2 + MyBatis-Plus（{@code InvoiceRepositoryImplH2Test} 先例）。
 */
@DisplayName("InvoiceTitleRepositoryImpl H2 集成测试（默认唯一/全量更新清列/软删）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceTitleRepositoryImplH2Test {

    private static final long USER_ID = 100L;
    private static final long OTHER_USER_ID = 999L;
    private static final String TAX_NO = "91110000123456789X";

    private static InvoiceTitleRepository repository;
    private static DataSource dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        // 税号三字段加密需要 AES/HMAC 密钥（authorization 先例）
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));

        dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:invoicetitledb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE invoice_title (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    type TINYINT NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    tax_no_cipher VARCHAR(512) NULL,
                    tax_no_hash VARCHAR(128) NULL,
                    tax_no_mask VARCHAR(64) NULL,
                    is_default TINYINT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_user_default UNIQUE (user_id, is_default)
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InvoiceTitleMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        repository = new InvoiceTitleRepositoryImpl(
                sqlSessionFactory.openSession().getMapper(InvoiceTitleMapper.class));
    }

    /** 保存企业抬头（isDefault 传 {@code InvoiceTitle.DEFAULT_FLAG} 或 null），回填 id。 */
    private InvoiceTitle saveCompanyTitle(long userId, Integer isDefault, String name) {
        return repository.save(InvoiceTitle.create(userId, 2, name, TAX_NO, isDefault != null));
    }

    @Test
    @Order(1)
    @DisplayName("保存与查询 — save 回填 id；findById / findByIdAndUserId 命中；多条非默认行不撞 uk（多 NULL 语义）")
    void shouldSaveAndFind_withMultipleNullDefaults() {
        InvoiceTitle first = saveCompanyTitle(USER_ID, null, "抬头一");
        InvoiceTitle second = saveCompanyTitle(USER_ID, null, "抬头二");

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals("抬头一", repository.findById(first.getId()).getTitle());
        assertEquals("抬头二", repository.findByIdAndUserId(second.getId(), USER_ID).getTitle());
        assertNull(repository.findByIdAndUserId(first.getId(), OTHER_USER_ID), "非本人返回 null（INV1 引用守卫）");
    }

    @Test
    @Order(2)
    @DisplayName("默认唯一 — 未清旧默认再设默认 → 撞 uk_user_default；清旧后可再设")
    void shouldEnforceUk_whenDoubleDefault() {
        saveCompanyTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG, "默认一");

        assertThrows(RuntimeException.class, () -> saveCompanyTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG, "默认二"),
                "uk_user_default 兜底「一用户至多一条默认」");

        assertEquals(1, repository.clearDefaultFlag(USER_ID), "清除本人旧默认（is_default→NULL）");
        assertNull(repository.findByIdAndUserId(findTitleId("默认一"), USER_ID).getIsDefault());

        saveCompanyTitle(USER_ID, InvoiceTitle.DEFAULT_FLAG, "默认二");
        assertEquals(InvoiceTitle.DEFAULT_FLAG,
                repository.findByIdAndUserId(findTitleId("默认二"), USER_ID).getIsDefault());
    }

    @Test
    @Order(3)
    @DisplayName("全量更新 — 设默认落库；企业转个人时税号三字段与 is_default 置 NULL 清列")
    void shouldUpdateFullWithNullClearing_whenCompanyTurnsPersonal() {
        // 换设默认前置清旧（应用层编排口径），避免与 Order 2 遗留默认行撞 uk_user_default
        repository.clearDefaultFlag(USER_ID);
        InvoiceTitle company = saveCompanyTitle(USER_ID, null, "某公司");

        // 第一步：更新为默认企业抬头（is_default 1 落库）
        InvoiceTitle defaulted = InvoiceTitle.create(USER_ID, 2, "某公司", TAX_NO, true);
        defaulted.setId(company.getId());
        defaulted.setUserId(USER_ID);
        assertEquals(1, repository.updateFull(defaulted), "本人行命中");
        assertEquals(InvoiceTitle.DEFAULT_FLAG, repository.findById(company.getId()).getIsDefault());

        // 第二步：全量替换为个人非默认——税号三字段与默认标记都必须被清成 NULL
        InvoiceTitle updated = InvoiceTitle.create(USER_ID, 1, "张三", null, false);
        updated.setId(company.getId());
        updated.setUserId(USER_ID);
        assertEquals(1, repository.updateFull(updated), "本人行命中");

        InvoiceTitle reloaded = repository.findById(company.getId());
        assertEquals(1, reloaded.getType());
        assertEquals("张三", reloaded.getTitle());
        assertNull(reloaded.getTaxNo(), "税号三字段必须清空（MP updateById 忽略空值做不到）");
        assertNull(reloaded.getIsDefault(), "取消默认必须置 NULL");
    }

    @Test
    @Order(4)
    @DisplayName("全量更新归属 — userId 不匹配（非本人）→ 0 行不落库")
    void shouldNotUpdateFull_whenNotOwner() {
        InvoiceTitle others = saveCompanyTitle(OTHER_USER_ID, null, "他人抬头");

        InvoiceTitle updated = InvoiceTitle.create(USER_ID, 1, "张三", null, false);
        updated.setId(others.getId());
        updated.setUserId(USER_ID);
        assertEquals(0, repository.updateFull(updated), "WHERE user_id 不匹配 → 0 行");
        assertEquals("他人抬头", repository.findByIdAndUserId(others.getId(), OTHER_USER_ID).getTitle(),
                "他人行未被改动");
    }

    @Test
    @Order(5)
    @DisplayName("软删 — deleteByIdAndUserId 仅删本人行；@TableLogic 过滤已删行")
    void shouldSoftDeleteOnlyOwnedRow() throws Exception {
        InvoiceTitle mine = saveCompanyTitle(USER_ID, null, "我的抬头");
        saveCompanyTitle(USER_ID, null, "我的抬头二");
        InvoiceTitle others = saveCompanyTitle(OTHER_USER_ID, null, "软删对照");

        repository.deleteByIdAndUserId(mine.getId(), USER_ID);

        assertNull(repository.findById(mine.getId()), "软删后 findById 过滤已删行");
        assertNull(repository.findByIdAndUserId(mine.getId(), USER_ID));
        assertTrue(repository.listByUserId(USER_ID).stream().noneMatch(t -> "我的抬头".equals(t.getTitle())));
        assertTrue(repository.listByUserId(USER_ID).stream().anyMatch(t -> "我的抬头二".equals(t.getTitle())));
        assertNotNull(repository.findByIdAndUserId(others.getId(), OTHER_USER_ID), "他人行不受影响");
        assertEquals(1, countRawRowsById(mine.getId()), "软删是 UPDATE 而非物理删除");
    }

    @Test
    @Order(6)
    @DisplayName("列表 — listByUserId 按创建时间倒序返回")
    void shouldListByCreateTimeDesc() throws Exception {
        InvoiceTitle early = saveCompanyTitle(USER_ID, null, "排序-早");
        InvoiceTitle late = saveCompanyTitle(USER_ID, null, "排序-晚");

        // H2 当前时间戳同秒精度，显式拉开 create_time 保证排序可断言
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE invoice_title SET create_time = '"
                    + LocalDateTime.now().minusDays(2).toString().replace('T', ' ')
                    + "' WHERE id = " + early.getId());
            stmt.execute("UPDATE invoice_title SET create_time = '"
                    + LocalDateTime.now().toString().replace('T', ' ')
                    + "' WHERE id = " + late.getId());
        }

        List<InvoiceTitle> titles = repository.listByUserId(USER_ID);
        assertTrue(titles.indexOf(findByTitle(titles, "排序-晚"))
                < titles.indexOf(findByTitle(titles, "排序-早")), "创建时间倒序：晚创建的排前面");
    }

    private InvoiceTitle findByTitle(List<InvoiceTitle> titles, String name) {
        return titles.stream().filter(t -> name.equals(t.getTitle())).findFirst().orElseThrow();
    }

    /** 直查指定 id 原始行数（绕过 @TableLogic，验证软删语义）。 */
    private int countRawRowsById(long id) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM invoice_title WHERE id = " + id)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 按抬头名查 id（断言辅助，仅扫本人未删行）。 */
    private Long findTitleId(String name) {
        return repository.listByUserId(USER_ID).stream()
                .filter(t -> name.equals(t.getTitle()))
                .findFirst()
                .map(InvoiceTitle::getId)
                .orElseThrow();
    }
}
