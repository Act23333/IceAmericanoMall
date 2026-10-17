package org.icedamericanomall.cart.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.cart.adapter.assembler.CartConverter;
import org.icedamericanomall.cart.adapter.assembler.CartConverterImpl;
import org.icedamericanomall.cart.adapter.request.CartAddReq;
import org.icedamericanomall.cart.adapter.request.CartUpdateReq;
import org.icedamericanomall.cart.app.port.CartSkuViewQuery;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.cart.infrastructure.persistence.CartMapper;
import org.icedamericanomall.cart.infrastructure.persistence.CartRepositoryImpl;
import org.icedamericanomall.cart.infrastructure.persistence.CartSkuViewQueryImpl;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * CartManager H2 集成测试 —— 加购去重合并 / 数量更新(≤0删除) / 选中切换 / 选中项查询 / 清空，
 * 以及<b>业务键（ADR-061）端到端</b>：线格式入参是 SKU 业务键，落库仍是技术键
 * （{@code cart.sku_id BIGINT}，A 案），出参再次换回业务键。
 *
 * <p>真实仓储（H2 + MyBatis-Plus）+ 真实聚合工厂 + <b>真实 SKU 读模型 SQL</b>
 * （{@code CartSkuViewQueryImpl}，同库只读 {@code sku LEFT JOIN spu}）——
 * 因此「业务键 → 技术键」这一步的 SQL 口径（BINARY(16) 用 {@code byte[]} 比较、
 * 走 {@code sku.sku_id} 唯一索引）也被本测试覆盖，而不是被 mock 掉。
 *
 * <p><b>键集纪律（批次 U1）</b>：{@code cart} 的 {@code uk_user_sku} 与 {@code Initialize.sql} 一致，
 * 并有用例<b>显式依赖</b>它（并发撞键 → 增量合并、重复插入 → {@code DuplicateKeyException}）——
 * 少了这个键，{@code CartManager} 的并发兜底路径在测试里根本不可达。
 */
@DisplayName("CartManager H2 集成测试（含 ADR-061 业务键端到端）")
class CartManagerH2Test {

    /** SKU 业务键（H2 的 BINARY(16)，与 MySQL 同形）。 */
    private static final String SKU_A = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SKU_B = "112233445566778899aabbccddeeff00";
    private static final String SPU_BIZ = "aabbccddeeff00112233445566778899";

    private CartManager cartManager;
    private CartRepository cartRepository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:cart_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        // 键集与 Initialize.sql:405-419 逐字一致：撤掉 uk_user_sku 就等于撤掉
        // CartManager 的「并发增量合并」兜底 —— 那条路径只能由 DB 唯一键触发，应用层没有等价 if。
        jdbcTemplate.execute("""
                CREATE TABLE cart (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
                    quantity INT DEFAULT 1, selected BOOLEAN DEFAULT TRUE,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_user_sku UNIQUE (user_id, sku_id)
                )""");
        // 读模型的数据源：真实 sku / spu 两表（列口径与 Initialize.sql 对齐）
        jdbcTemplate.execute("""
                CREATE TABLE sku (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    sku_id BINARY(16) NOT NULL,
                    spu_id BIGINT,
                    spec VARCHAR(200), price INT, image VARCHAR(255),
                    CONSTRAINT uk_sku_id UNIQUE (sku_id)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE spu (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    spu_id BINARY(16) NOT NULL,
                    name VARCHAR(200), main_image VARCHAR(255),
                    CONSTRAINT uk_spu_id UNIQUE (spu_id)
                )""");
        jdbcTemplate.update("INSERT INTO spu (id, spu_id, name, main_image) VALUES (?,?,?,?)",
                10L, IdGenerator.toBytes(SPU_BIZ), "手工陶瓷茶杯", "https://cdn/main.jpg");
        jdbcTemplate.update("INSERT INTO sku (id, sku_id, spu_id, spec, price, image) VALUES (?,?,?,?,?,?)",
                1000L, IdGenerator.toBytes(SKU_A), 10L, "天青/标准", 12800, "img-a.jpg");
        jdbcTemplate.update("INSERT INTO sku (id, sku_id, spu_id, spec, price, image) VALUES (?,?,?,?,?,?)",
                1001L, IdGenerator.toBytes(SKU_B), 10L, "月白/大号", 9900, "img-b.jpg");

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(CartMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // 生产同款装配：SqlSessionTemplate 挂 Spring 异常翻译器（H2 23505 → DuplicateKeyException）。
        // 裸 openSession() 只能抛 MyBatis PersistenceException —— 唯一键冲突到不了 CartManager 的
        // catch，「并发合并」在测试里被静默绕过（本批要修的可验证性缺口）。
        cartRepository = new CartRepositoryImpl(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(CartMapper.class));
        CartConverter converter = new CartConverterImpl();
        CartSkuViewQuery query = new CartSkuViewQueryImpl(jdbcTemplate);
        cartManager = new CartManager(cartRepository, query, converter);
    }

    private CartAddReq addReq(String skuBusinessId, int qty) {
        CartAddReq r = new CartAddReq();
        r.setSkuId(skuBusinessId);
        r.setQuantity(qty);
        return r;
    }

    private CartUpdateReq updateReq(String skuBusinessId, int qty) {
        CartUpdateReq r = new CartUpdateReq();
        r.setSkuId(skuBusinessId);
        r.setQuantity(qty);
        return r;
    }

    @Test
    @DisplayName("addItem — 入参收 SKU 业务键，落库仍是技术键（cart.sku_id 技术外键不动，A 案）")
    void shouldResolveBusinessKeyToTechnicalId_whenAdding() {
        cartManager.addItem(1L, addReq(SKU_A, 2));

        List<CartItem> items = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, items.size());
        assertEquals(1000L, items.get(0).getSkuId(), "落库应为 sku.id 技术键（同库内部外键保持不动）");
    }

    @Test
    @DisplayName("addItem — 相同 SKU（业务键）累加数量而非新增行（去重合并）")
    void shouldMergeQuantity_whenSameSku() {
        cartManager.addItem(1L, addReq(SKU_A, 2));
        cartManager.addItem(1L, addReq(SKU_A, 3));

        List<CartItem> items = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, items.size(), "同一 SKU 不应产生第二行");
        assertEquals(5, items.get(0).getQuantity());
        assertTrue(items.get(0).getSelected());
    }

    /**
     * 拆分守卫（批次 ③「拆双义错误码」，H2 端到端口径）：<b>形态非法 → 400 / 1101</b>，不落行。
     *
     * <p><b>改前必失败</b>：改前本用例（原名 {@code shouldReject_whenBusinessKeyInvalid}）
     * 只断言「抛 {@code BizException}」，实际拿到的是 {@code SKU_NOT_FOUND(3016)} ——
     * 「形态非法」与「SKU 不存在」在真实仓储链路上被同一个码吞掉。现在改为断言
     * {@link BadRequestException} + {@code PARAM_ERROR}，旧实现<b>必然</b>红。
     */
    @Test
    @DisplayName("addItem — 业务键形态非法 → PARAM_ERROR(400/1101)，且不落任何行（fail-fast）")
    void shouldRejectMalformed_whenBusinessKeyInvalid() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> cartManager.addItem(1L, addReq("prod-001", 1)));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    /**
     * 另一半（H2 端到端）：<b>形态合法但 SKU 不存在 → SKU_NOT_FOUND(3016)</b>，同样不落行。
     *
     * <p>与上一条用例<b>共享同一张 H2 表</b>、只在入参形态上不同 —— 于是「两个语义被写成一个码」
     * 这类回归无法伪装成通过。
     */
    @Test
    @DisplayName("addItem — 形态合法但 SKU 不存在 → SKU_NOT_FOUND(3016)，且不落任何行（fail-closed）")
    void shouldRejectAbsent_whenBusinessKeyWellFormed() {
        String unknownWellFormed = "deadbeefdeadbeefdeadbeefdeadbeef";

        BizException ex = assertThrows(BizException.class,
                () -> cartManager.addItem(1L, addReq(unknownWellFormed, 1)));
        assertEquals(ErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
        assertNotEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("updateItem — 数量≤0 则删除该项")
    void shouldRemove_whenQuantityZero() {
        cartManager.addItem(1L, addReq(SKU_A, 2));
        cartManager.updateItem(1L, updateReq(SKU_A, 0));
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("updateItem — 负数同样触发删除（CartUpdateReq 不再有 @Min(1) 下限）")
    void shouldRemove_whenQuantityNegative() {
        cartManager.addItem(1L, addReq(SKU_A, 2));
        cartManager.updateItem(1L, updateReq(SKU_A, -1));
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("selectItem — 切换选中状态（业务键入参），getSelectedItemCos 只返回选中项且出业务键")
    void shouldToggleSelectAndListSelected() {
        cartManager.addItem(1L, addReq(SKU_A, 1));
        cartManager.addItem(1L, addReq(SKU_B, 1));
        cartManager.selectItem(1L, SKU_B, false);

        List<org.icedamericanomall.dto.CartItemCO> selected = cartManager.getSelectedItemCos(1L);
        assertEquals(1, selected.size());
        assertEquals(SKU_A, selected.get(0).getSkuId(), "I14 出参为业务键（技术键 1000 不得出现）");
        assertEquals(Integer.valueOf(1), selected.get(0).getQuantity());
    }

    @Test
    @DisplayName("getSelectedItemCos — I14 端到端：cartItemId 回传真实主键（防映射缺口回归，trade 据此删已购项）")
    void shouldCarryPersistedCartItemId_whenListingSelectedItems() {
        cartManager.addItem(1L, addReq(SKU_A, 2));
        Long rowId = cartRepository.findByUserIdOrderByCreateTimeDesc(1L).get(0).getId();

        List<org.icedamericanomall.dto.CartItemCO> selected = cartManager.getSelectedItemCos(1L);

        assertEquals(1, selected.size());
        assertEquals(rowId, selected.get(0).getCartItemId(),
                "cartItemId 必须是落库主键：为 null 时 trade 侧删车会静默跳过（真仓储 + 真 CartConverterImpl 链路）");
        assertTrue(selected.stream().allMatch(co -> co.getCartItemId() != null),
                "I14 明细不允许出现无主键项");
    }

    @Test
    @DisplayName("getCartView — 真实读模型 SQL：sku_id/spu_id 出业务键、spu_name/price 回填")
    void shouldEnrichBusinessKeysAndDisplay_fromRealSql() {
        cartManager.addItem(1L, addReq(SKU_A, 2));

        var vo = cartManager.getCartView(1L);

        assertEquals(1, vo.getItems().size());
        var item = vo.getItems().get(0);
        assertEquals(SKU_A, item.getSkuId(), "SKU 业务键（32-hex）");
        assertEquals(SPU_BIZ, item.getSpuId(), "SPU 业务键（32-hex）");
        assertEquals("手工陶瓷茶杯", item.getSpuName(), "ADR-061 §决策 5：spu_name ← spu.name");
        assertEquals(Integer.valueOf(12800), item.getPrice());
        assertEquals(Integer.valueOf(25600), item.getSubTotal());
    }

    @Test
    @DisplayName("clearCart — 清空用户购物车")
    void shouldClearCart() {
        cartManager.addItem(1L, addReq(SKU_A, 1));
        cartManager.addItem(1L, addReq(SKU_B, 1));
        cartManager.clearCart(1L);
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("deleteByIds — 仅删除指定购物车项（已下单商品，京东口径）")
    void shouldDeleteOnlySpecifiedItems() {
        cartManager.addItem(1L, addReq(SKU_A, 1));
        cartManager.addItem(1L, addReq(SKU_B, 1));
        Long keptId = cartRepository.findByUserIdOrderByCreateTimeDesc(1L).get(0).getId();

        cartManager.deleteByIds(1L, List.of(keptId));

        List<CartItem> remaining = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, remaining.size());
    }

    /**
     * <b>D4 承重用例 ①（本批新增）</b>：同 {@code (user_id, sku_id)} 二次插入必须由 DB 拦下。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：{@code CartRepositoryImpl#save} 里没有任何判重 if
     * （它就是 {@code cartMapper.insert}），仓储也不翻译异常 —— 拦下这次插入的只能是
     * {@code uk_user_sku}。本断言在<b>无</b>该键的 H2 DDL 上会失败：第二行插得进去，
     * {@code assertThrows} 拿不到异常，且表里变 2 行。
     */
    @Test
    @DisplayName("uk_user_sku — 同 (user_id, sku_id) 二次插入 → DuplicateKeyException（DB 唯一键抛出）")
    void shouldRejectDuplicateUserSku_byDatabaseKey() {
        cartRepository.save(CartItem.create(1L, 1000L, 1));

        assertThrows(DuplicateKeyException.class,
                () -> cartRepository.save(CartItem.create(1L, 1000L, 1)),
                "若 uk_user_sku 缺失，此行插入成功 → 本用例变红：唯一键才是并发兜底的载体");
        assertEquals(1, cartRepository.findByUserIdOrderByCreateTimeDesc(1L).size(), "不得出现第二行");
    }

    /**
     * <b>D4 承重用例 ②（本批新增）</b>：并发加购撞唯一键 → {@code CartManager} 转为增量合并。
     *
     * <p>竞态窗口用 spy 复现：另一请求已经把 {@code (user 1, sku 1000)} 那一行写进去，
     * 而本请求的「先查」落在窗口内（第一次判定「不存在」），于是走到 {@code save}。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：能进入 {@code catch (DuplicateKeyException)} 的唯一
     * 原因是 DB 抛了 23505（经 {@code SqlSessionTemplate} 翻译）；应用层没有任何 if 能判出「并发」。
     * 无 {@code uk_user_sku} 时插入成功 → 购物车里出现两行同 SKU（{@code size()==1} 断言变红），
     * 合并逻辑根本不执行。
     */
    @Test
    @DisplayName("uk_user_sku — 并发撞键：改为增量合并（不新增行、不 500）")
    void shouldMergeQuantity_whenConcurrentInsertHitsUniqueKey() {
        // 另一请求已落库的那一行（数量 2）
        cartRepository.save(CartItem.create(1L, 1000L, 2));

        CartRepositoryImpl racingRepository = spy((CartRepositoryImpl) cartRepository);
        // 只有第一次「先查」落在竞态窗口内；撞键后的回查走真实实现
        doReturn(Optional.empty()).doCallRealMethod()
                .when(racingRepository).findByUserIdAndSkuId(anyLong(), anyLong());
        CartManager racingManager = new CartManager(racingRepository,
                new CartSkuViewQueryImpl(jdbcTemplate), new CartConverterImpl());

        racingManager.addItem(1L, addReq(SKU_A, 3));

        List<CartItem> items = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, items.size(), "撞键后必须合并到既有一行，不得新增第二行");
        assertEquals(5, items.get(0).getQuantity(), "2 + 3：走的是 incrementQuantity 合并分支");
    }

    /**
     * <b>键集断言（本批新增）</b>：{@code cart} 表唯一键集与 {@code Initialize.sql:413-414} 一致
     * （{@code uk_user_sku(user_id, sku_id)}，列序不差）。缺一即红。
     */
    @Test
    @DisplayName("DDL 键集 — cart 表的 uk_user_sku(user_id, sku_id) 与 Initialize.sql 一致")
    void shouldExposeProductionUniqueKeySet() {
        Map<String, List<String>> keys = uniqueKeysOf("cart");

        assertEquals(Set.of("uk_user_sku"), keys.keySet(), "生产唯一键（业务键）只有 uk_user_sku");
        assertEquals(List.of("user_id", "sku_id"), keys.get("uk_user_sku"), "列序必须与生产一致");
    }

    /**
     * 该表的唯一键 → 有序列集（列顺序取自 {@code KEY_COLUMN_USAGE.ORDINAL_POSITION}）。
     *
     * @param table 表名（本测试 DATABASE_TO_LOWER=TRUE，传小写）
     * @return 键名 → 列名列表
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
}
