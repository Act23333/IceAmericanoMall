package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.config.AuditFieldMetaObjectHandler;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InvoiceLinkCompensationRepositoryImpl H2 集成测试 —— <b>用真实数据库约束</b>证明补偿记录的幂等。
 *
 * <p><b>为什么要 H2 而不是只 mock Mapper</b>：「同一 {@code (order_no, scene_ref, kind)} 只留一行」
 * 这条不变式的唯一执行者是 {@code uk_order_scene_kind}（{@code Initialize.sql}）。
 * 只 mock {@code insert} 抛 {@code DuplicateKeyException}，证明的只是 {@code catch} 写对了 ——
 * 键集若与 DDL 不一致（漏列、顺序错、根本没建键），测试照样绿。本类把键集按
 * {@code Initialize.sql} 逐字建出来，冲突由 H2 23505 → Spring 异常翻译器抛出。
 */
@DisplayName("InvoiceLinkCompensationRepositoryImpl H2 集成测试（uk_order_scene_kind 幂等）")
class InvoiceLinkCompensationH2Test {

    /** 订单号（32 位 hex 业务键）。 */
    private static final String ORDER_NO = "bbbbbbbb000070008000000000000001";
    /** 另一笔订单号（验证锚点不串号）。 */
    private static final String OTHER_ORDER_NO = "bbbbbbbb000070008000000000000002";
    private static final String SCENE_REF = "AS:9";

    private InvoiceLinkCompensationRepositoryImpl repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:invoice_link_comp_" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        // 键集/列口径与 Initialize.sql（invoice_link_compensation）逐字一致：撤掉 uk_order_scene_kind
        // 就等于撤掉「重复联动失败只留一行」的兜底 —— 那条路径只能由 DB 唯一键触发，应用层没有等价 if。
        jdbcTemplate.execute("""
                CREATE TABLE invoice_link_compensation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no BINARY(16) NOT NULL,
                    scene_ref VARCHAR(64) NOT NULL,
                    kind TINYINT NOT NULL,
                    status TINYINT NOT NULL DEFAULT 0,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP NULL,
                    last_error VARCHAR(500) NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_order_scene_kind UNIQUE (order_no, scene_ref, kind)
                )""");

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        // 空 GlobalConfig 的 dbConfig 为 null：提前注册时 TableInfo 解析会直接 NPE，故显式给默认值
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        // 生产同款审计字段填充器（ia-common 唯一实现）：create_time / update_time 由它填。
        // 少了它，@TableField(fill=...) 的两列会被显式写成 NULL（而不是走列默认值）—— 入库直接炸，
        // 这正是「测试装配与生产不一致」的假红。
        globalConfig.setMetaObjectHandler(new AuditFieldMetaObjectHandler());
        // 必须先于 addMapper 注册：MP 的 GlobalConfigUtils#setGlobalConfig 是 putIfAbsent，
        // 而 addMapper 解析 TableInfo 时就会给 configuration 落一份默认 GlobalConfig（无填充器），
        // 事后再 set 静默无效（审计字段从此填不上）。
        GlobalConfigUtils.setGlobalConfig(config, globalConfig);
        config.addMapper(InvoiceLinkCompensationMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // 生产同款装配：SqlSessionTemplate 挂 Spring 异常翻译器（H2 23505 → DuplicateKeyException），
        // 裸 openSession() 只能抛 PersistenceException —— 唯一键冲突到不了仓储的 catch。
        repository = new InvoiceLinkCompensationRepositoryImpl(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(InvoiceLinkCompensationMapper.class));
    }

    private InvoiceLinkCompensation pending(String orderNo, String sceneRef, InvoiceLinkKindEnum kind) {
        return InvoiceLinkCompensation.newPending(orderNo, sceneRef, kind, "BizException[SYSTEM_BUSY]: 系统繁忙");
    }

    private Map<String, Object> onlyRow() {
        return jdbcTemplate.queryForMap("SELECT order_no, scene_ref, kind, status, retry_count, "
                + "next_retry_at, last_error FROM invoice_link_compensation");
    }

    private int rowCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_link_compensation",
                Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("落库 — PENDING 记录：order_no 为 16 字节原始形态、kind=1、status=0、retry_count=0、next_retry_at 非空")
    void shouldInsertPendingRow_whenCreatePending() {
        boolean created = repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID));

        assertTrue(created, "首次写入必须新建");
        assertEquals(1, rowCount());
        Map<String, Object> row = onlyRow();
        assertArrayEquals(IdGenerator.toBytes(ORDER_NO), (byte[]) row.get("order_no"),
                "order_no 必须以 BINARY(16) 原始字节落库（ADR-050）");
        assertEquals(SCENE_REF, row.get("scene_ref"));
        assertEquals(InvoiceLinkKindEnum.VOID.getCode(), ((Number) row.get("kind")).intValue());
        assertEquals(InvoiceLinkCompensationStatusEnum.PENDING.getCode(), ((Number) row.get("status")).intValue());
        assertEquals(0, ((Number) row.get("retry_count")).intValue());
        assertNotNull(row.get("next_retry_at"), "next_retry_at 必须落当前时间（调度器就绪后即刻可拾取）");
        assertTrue(String.valueOf(row.get("last_error")).contains("SYSTEM_BUSY"));
    }

    @Test
    @DisplayName("幂等 — 同一 (order_no, scene_ref, kind) 二次写入：返回 false、不抛、库里仍只有一行")
    void shouldKeepSingleRow_whenSameTripleInsertedTwice() {
        assertTrue(repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID)));

        boolean second = repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID));

        assertFalse(second, "撞 uk_order_scene_kind 必须归一为幂等命中（返回 false，不抛）");
        assertEquals(1, rowCount(), "同一锚点重复触发不得堆积第二行");
    }

    @Test
    @DisplayName("kind 参与锚点 — 同订单同场景的 VOID 与 PARTIAL 是两条独立补偿（互不吞并）")
    void shouldKeepTwoRows_whenKindDiffers() {
        assertTrue(repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID)));
        assertTrue(repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.PARTIAL)));

        assertEquals(2, rowCount(), "先部分退款、后全额退款是两个补偿动作，kind 必须参与唯一键");
    }

    @Test
    @DisplayName("锚点不串号 — 不同 order_no 同一 scene_ref/kind 各留一行")
    void shouldKeepTwoRows_whenOrderNoDiffers() {
        assertTrue(repository.createPending(pending(ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID)));
        assertTrue(repository.createPending(pending(OTHER_ORDER_NO, SCENE_REF, InvoiceLinkKindEnum.VOID)));

        assertEquals(2, rowCount());
    }
}
