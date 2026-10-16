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
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.cart.domain.repository.CartRepository;
import org.icedamericanomall.cart.infrastructure.persistence.CartMapper;
import org.icedamericanomall.cart.infrastructure.persistence.CartRepositoryImpl;
import org.icedamericanomall.client.SkuClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * CartManager H2 集成测试 —— 加购去重合并 / 数量更新(≤0删除) / 选中切换 / 选中项查询 / 清空。
 *
 * <p>真实仓储（H2 + MyBatis-Plus）+ 真实聚合工厂，仅 mock SkuClient（跨服务回填不在本测）。
 */
@DisplayName("CartManager H2 集成测试")
class CartManagerH2Test {

    private CartManager cartManager;
    private CartRepository cartRepository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:cart_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE cart (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
                    quantity INT DEFAULT 1, selected BOOLEAN DEFAULT TRUE,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        }

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

        cartRepository = new CartRepositoryImpl(sqlSessionFactory.openSession().getMapper(CartMapper.class));
        CartConverter converter = new CartConverterImpl();
        cartManager = new CartManager(cartRepository, mock(SkuClient.class), converter);
    }

    private CartAddReq addReq(Long skuId, int qty) {
        CartAddReq r = new CartAddReq();
        r.setSkuId(skuId);
        r.setQuantity(qty);
        return r;
    }

    private CartUpdateReq updateReq(Long skuId, int qty) {
        CartUpdateReq r = new CartUpdateReq();
        r.setSkuId(skuId);
        r.setQuantity(qty);
        return r;
    }

    @Test
    @DisplayName("addItem — 相同 SKU 累加数量而非新增行（去重合并）")
    void shouldMergeQuantity_whenSameSku() {
        cartManager.addItem(1L, addReq(1000L, 2));
        cartManager.addItem(1L, addReq(1000L, 3));

        List<CartItem> items = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, items.size(), "同一 SKU 不应产生第二行");
        assertEquals(5, items.get(0).getQuantity());
        assertTrue(items.get(0).getSelected());
    }

    @Test
    @DisplayName("updateItem — 数量≤0 则删除该项")
    void shouldRemove_whenQuantityZero() {
        cartManager.addItem(1L, addReq(1000L, 2));
        cartManager.updateItem(1L, updateReq(1000L, 0));
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("updateItem — 负数同样触发删除（CartUpdateReq 不再有 @Min(1) 下限）")
    void shouldRemove_whenQuantityNegative() {
        cartManager.addItem(1L, addReq(1000L, 2));
        cartManager.updateItem(1L, updateReq(1000L, -1));
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("selectItem — 切换选中状态，getSelectedItemCos 只返回选中项")
    void shouldToggleSelectAndListSelected() {
        cartManager.addItem(1L, addReq(1000L, 1));
        cartManager.addItem(1L, addReq(1001L, 1));
        cartManager.selectItem(1L, 1001L, false);

        List<org.icedamericanomall.dto.CartItemCO> selected = cartManager.getSelectedItemCos(1L);
        assertEquals(1, selected.size());
        assertEquals(1000L, selected.get(0).getSkuId());
        assertEquals(Integer.valueOf(1), selected.get(0).getQuantity());
    }

    @Test
    @DisplayName("getSelectedItemCos — I14 端到端：cartItemId 回传真实主键（防映射缺口回归，trade 据此删已购项）")
    void shouldCarryPersistedCartItemId_whenListingSelectedItems() {
        cartManager.addItem(1L, addReq(1000L, 2));
        Long rowId = cartRepository.findByUserIdOrderByCreateTimeDesc(1L).get(0).getId();

        List<org.icedamericanomall.dto.CartItemCO> selected = cartManager.getSelectedItemCos(1L);

        assertEquals(1, selected.size());
        assertEquals(rowId, selected.get(0).getCartItemId(),
                "cartItemId 必须是落库主键：为 null 时 trade 侧删车会静默跳过（真仓储 + 真 CartConverterImpl 链路）");
        assertTrue(selected.stream().allMatch(co -> co.getCartItemId() != null),
                "I14 明细不允许出现无主键项");
    }

    @Test
    @DisplayName("clearCart — 清空用户购物车")
    void shouldClearCart() {
        cartManager.addItem(1L, addReq(1000L, 1));
        cartManager.addItem(1L, addReq(1001L, 1));
        cartManager.clearCart(1L);
        assertTrue(cartRepository.findByUserIdOrderByCreateTimeDesc(1L).isEmpty());
    }

    @Test
    @DisplayName("deleteByIds — 仅删除指定购物车项（已下单商品，京东口径）")
    void shouldDeleteOnlySpecifiedItems() {
        cartManager.addItem(1L, addReq(1000L, 1));
        cartManager.addItem(1L, addReq(1001L, 1));
        Long keptId = cartRepository.findByUserIdOrderByCreateTimeDesc(1L).get(0).getId();

        cartManager.deleteByIds(1L, List.of(keptId));

        List<CartItem> remaining = cartRepository.findByUserIdOrderByCreateTimeDesc(1L);
        assertEquals(1, remaining.size());
    }
}
