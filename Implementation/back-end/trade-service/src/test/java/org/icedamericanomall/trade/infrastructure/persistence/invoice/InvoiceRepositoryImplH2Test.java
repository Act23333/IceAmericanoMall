package org.icedamericanomall.trade.infrastructure.persistence.invoice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2 集成测试 — 验证 {@code InvoiceRepositoryImpl} 条件迁移状态机 SQL 语义
 * （开具 1→2 / 作废 1→4 / 冲红 2→3 各自命中与 0 行分支、终态让出有效票槽位、
 * 全额退款联动互斥迁移与幂等空操作、催开仅待开具可记）。
 * 不使用 Spring Boot 上下文（无需 Nacos/Redis/Feign），直接构建 H2 + MyBatis-Plus。
 */
@DisplayName("InvoiceRepositoryImpl H2 集成测试（条件迁移状态机）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoiceRepositoryImplH2Test {

    private static InvoiceRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:invoicedb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE invoice (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    invoice_no VARCHAR(64) NOT NULL UNIQUE,
                    order_no VARCHAR(64) NOT NULL,
                    active_flag INT NULL,
                    user_id BIGINT NOT NULL,
                    seller_id BIGINT NOT NULL,
                    type INT NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    tax_no_cipher VARCHAR(512) NULL,
                    tax_no_hash VARCHAR(128) NULL,
                    tax_no_mask VARCHAR(64) NULL,
                    email_cipher VARCHAR(512) NULL,
                    email_hash VARCHAR(128) NULL,
                    email_mask VARCHAR(64) NULL,
                    amount INT NOT NULL,
                    status INT NOT NULL,
                    issue_time TIMESTAMP NULL,
                    mock_invoice_no VARCHAR(64) NULL,
                    pdf_key VARCHAR(255) NULL,
                    red_for_id BIGINT NULL,
                    red_reason VARCHAR(255) NULL,
                    partial_refund_flag INT DEFAULT 0,
                    urge_time TIMESTAMP NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_order_active UNIQUE (order_no, active_flag)
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InvoiceMapper.class);
        // 分页插件（selectPage 依赖 total/limit 语义，生产由 MybatisPlusConfig 注册）
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        config.addInterceptor(interceptor);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        repository = new InvoiceRepositoryImpl(
                sqlSessionFactory.openSession().getMapper(InvoiceMapper.class));
    }

    private Invoice saveInvoice(String invoiceNo, String orderNo, int status) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setOrderNo(orderNo);
        // 终态（3/4）不占 uk_order_active 有效票槽位（多 NULL 语义）
        invoice.setActiveFlag(status == InvoiceStatusEnum.RED_FLUSHED.getCode()
                || status == InvoiceStatusEnum.VOIDED.getCode() ? null : 1);
        invoice.setUserId(100L);
        invoice.setSellerId(200L);
        invoice.setType(1);
        invoice.setTitle("张三");
        invoice.setAmount(12550);
        invoice.setStatus(status);
        invoice.setPartialRefundFlag(0);
        return repository.save(invoice);
    }

    @Test
    @Order(1)
    @DisplayName("开具 — 条件迁移 1→2 命中回填票号/时间/样票；再开一次 0 行")
    void shouldIssueOnlyOnce() {
        saveInvoice("INV-H2-1", "ORD-H2-1", InvoiceStatusEnum.PENDING_ISSUE.getCode());

        int rows = repository.issueIfPending("INV-H2-1", "MOCK-AAAA0001", "invoice/INV-H2-1.pdf",
                LocalDateTime.now());
        assertEquals(1, rows, "待开具命中");

        Invoice issued = repository.findByInvoiceNo("INV-H2-1");
        assertEquals(InvoiceStatusEnum.ISSUED.getCode(), issued.getStatus());
        assertEquals("MOCK-AAAA0001", issued.getMockInvoiceNo());
        assertEquals("invoice/INV-H2-1.pdf", issued.getPdfKey());
        assertNotNull(issued.getIssueTime());
        assertEquals(1, issued.getActiveFlag(), "已开具仍占有效票槽位");

        assertEquals(0, repository.issueIfPending("INV-H2-1", "MOCK-AAAA0002",
                "invoice/x.pdf", LocalDateTime.now()), "已开具再开 = 0 行状态冲突");
    }

    @Test
    @Order(2)
    @DisplayName("冲红 — 条件迁移 2→3 命中并置 active_flag=NULL；重复冲红 0 行（幂等）")
    void shouldRedFlushOnceAndExpireActiveSlot() {
        saveInvoice("INV-H2-2", "ORD-H2-2", InvoiceStatusEnum.ISSUED.getCode());

        assertEquals(1, repository.redFlushIfIssued("INV-H2-2", "部分退款人工处理"));
        Invoice red = repository.findByInvoiceNo("INV-H2-2");
        assertEquals(InvoiceStatusEnum.RED_FLUSHED.getCode(), red.getStatus());
        assertNull(red.getActiveFlag(), "终态让出 uk_order_active 槽位");
        assertEquals("部分退款人工处理", red.getRedReason());

        assertEquals(0, repository.redFlushIfIssued("INV-H2-2", "重复触发"), "已冲红 = 0 行");
    }

    @Test
    @Order(3)
    @DisplayName("冲红后重开 — 原票终态让位后同单可再插有效票（uk_order_active 多 NULL 语义）")
    void shouldReapplyAfterTerminal_whenSlotReleased() {
        saveInvoice("INV-H2-3", "ORD-H2-3", InvoiceStatusEnum.VOIDED.getCode());
        saveInvoice("INV-H2-3B", "ORD-H2-3", InvoiceStatusEnum.PENDING_ISSUE.getCode());

        Invoice active = repository.findActiveByOrderNo("ORD-H2-3");
        assertNotNull(active);
        assertEquals("INV-H2-3B", active.getInvoiceNo());
    }

    @Test
    @Order(4)
    @DisplayName("买家撤回 — 条件迁移 1→4 命中；终态再撤 0 行")
    void shouldCancelOnlyPending() {
        saveInvoice("INV-H2-4", "ORD-H2-4", InvoiceStatusEnum.PENDING_ISSUE.getCode());

        assertEquals(1, repository.cancelIfPending("INV-H2-4", null));
        assertEquals(InvoiceStatusEnum.VOIDED.getCode(),
                repository.findByInvoiceNo("INV-H2-4").getStatus());
        assertEquals(0, repository.cancelIfPending("INV-H2-4", null), "已作废再撤 = 0 行");
    }

    @Test
    @Order(5)
    @DisplayName("全额退款联动 — 待开具→作废(4)；已开具→冲红(3)；无有效票=0 幂等；结果互斥")
    void shouldVoidOrRedFlushOnFullRefund() {
        saveInvoice("INV-H2-5A", "ORD-H2-5A", InvoiceStatusEnum.PENDING_ISSUE.getCode());
        assertEquals(4, repository.voidOnFullRefund("ORD-H2-5A", "SYSTEM_FULL_REFUND:X"),
                "待开具有效票 → 自动作废");
        assertEquals(4, repository.findByInvoiceNo("INV-H2-5A").getStatus());
        assertTrue(repository.findByInvoiceNo("INV-H2-5A").getRedReason().startsWith("SYSTEM_FULL_REFUND:"));

        saveInvoice("INV-H2-5B", "ORD-H2-5B", InvoiceStatusEnum.ISSUED.getCode());
        assertEquals(3, repository.voidOnFullRefund("ORD-H2-5B", "SYSTEM_FULL_REFUND:Y"),
                "已开具有效票 → 自动冲红");
        assertEquals(3, repository.findByInvoiceNo("INV-H2-5B").getStatus());

        assertEquals(0, repository.voidOnFullRefund("ORD-H2-5A", "SYSTEM_FULL_REFUND:REPLAY"),
                "终态重放 = 幂等空操作");
        assertEquals(0, repository.voidOnFullRefund("ORD-H2-404", "SYSTEM_FULL_REFUND:NONE"),
                "无票 = 幂等空操作");
    }

    @Test
    @Order(6)
    @DisplayName("催开 — 仅待开具可记 urge_time；已开具 0 行")
    void shouldMarkUrgedOnlyWhenPending() {
        saveInvoice("INV-H2-6", "ORD-H2-6", InvoiceStatusEnum.PENDING_ISSUE.getCode());
        assertEquals(1, repository.markUrged("INV-H2-6", LocalDateTime.now()));
        assertNotNull(repository.findByInvoiceNo("INV-H2-6").getUrgeTime());

        saveInvoice("INV-H2-7", "ORD-H2-7", InvoiceStatusEnum.ISSUED.getCode());
        assertEquals(0, repository.markUrged("INV-H2-7", LocalDateTime.now()), "已开具不可催开");
    }

    @Test
    @Order(7)
    @DisplayName("管理端分页 — status/sellerId/partialRefund 筛选 + urgeOnly 催开排序视图")
    void shouldPageForAdmin_withFiltersAndUrgeOrder() {
        saveInvoice("INV-H2-8A", "ORD-H2-8A", InvoiceStatusEnum.PENDING_ISSUE.getCode());
        saveInvoice("INV-H2-8B", "ORD-H2-8B", InvoiceStatusEnum.PENDING_ISSUE.getCode());

        // 催开视图：仅看有催开记录的发票，按 urge_time 倒序（两条均为待开具方可记录）
        repository.markUrged("INV-H2-8A", LocalDateTime.now().minusHours(1));
        repository.markUrged("INV-H2-8B", LocalDateTime.now());

        var urged = repository.pageForAdmin(null, null, null, true, 1, 10);
        // INV-H2-6（Order 6 已记催开）+ INV-H2-8A/8B（本用例记催开）
        assertEquals(3, urged.getTotal());
        assertEquals("INV-H2-8B", urged.getRecords().get(0).getInvoiceNo(), "最近催开的排最前");

        var byStatus = repository.pageForAdmin(InvoiceStatusEnum.PENDING_ISSUE.getCode(), null,
                null, false, 1, 10);
        assertTrue(byStatus.getRecords().stream().allMatch(i -> i.getStatus() == 1));

        var bySeller = repository.pageForAdmin(null, 200L, null, false, 1, 10);
        assertTrue(bySeller.getTotal() >= 2);

        var partial = repository.pageForAdmin(null, null, true, false, 1, 10);
        assertEquals(0, partial.getTotal(), "无部分退款标记的发票");
    }
}
