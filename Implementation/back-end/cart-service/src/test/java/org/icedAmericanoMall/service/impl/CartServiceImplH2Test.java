package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedAmericanoMall.convert.CartConverter;
import org.icedAmericanoMall.dto.request.CartAddReq;
import org.icedAmericanoMall.dto.request.CartUpdateReq;
import org.icedAmericanoMall.mapper.CartMapper;
import org.icedAmericanoMall.pojo.CartEntity;
import org.icedAmericanoMall.service.ICartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * CartServiceImpl H2 集成测试 —— 加购去重合并 / 数量更新(≤0删除) / 选中切换 / 选中项查询。
 */
@DisplayName("CartServiceImpl H2 集成测试")
class CartServiceImplH2Test {

    private ICartService cartService;

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

        CartServiceImpl impl = new CartServiceImpl(mock(CartConverter.class));
        ReflectionTestUtils.setField(impl, "baseMapper",
                sqlSessionFactory.openSession().getMapper(CartMapper.class));
        cartService = impl;
    }

    private CartAddReq addReq(Long skuId, int qty) {
        CartAddReq r = new CartAddReq();
        r.setSkuId(skuId); r.setQuantity(qty);
        return r;
    }

    private CartUpdateReq updateReq(Long skuId, int qty) {
        CartUpdateReq r = new CartUpdateReq();
        r.setSkuId(skuId); r.setQuantity(qty);
        return r;
    }

    @Test
    @DisplayName("addItem — 相同 SKU 累加数量而非新增行（去重合并）")
    void shouldMergeQuantity_whenSameSku() {
        cartService.addItem(1L, addReq(1000L, 2));
        cartService.addItem(1L, addReq(1000L, 3));

        List<CartEntity> items = cartService.lambdaQuery().eq(CartEntity::getUserId, 1L).list();
        assertEquals(1, items.size(), "同一 SKU 不应产生第二行");
        assertEquals(5, items.get(0).getQuantity());
        assertTrue(items.get(0).getSelected());
    }

    @Test
    @DisplayName("updateItem — 数量≤0 则删除该项")
    void shouldRemove_whenQuantityZero() {
        cartService.addItem(1L, addReq(1000L, 2));
        cartService.updateItem(1L, updateReq(1000L, 0));
        assertTrue(cartService.lambdaQuery().eq(CartEntity::getUserId, 1L).list().isEmpty());
    }

    @Test
    @DisplayName("selectItem — 切换选中状态，getSelectedItems 只返回选中项")
    void shouldToggleSelectAndListSelected() {
        cartService.addItem(1L, addReq(1000L, 1));
        cartService.addItem(1L, addReq(1001L, 1));
        cartService.selectItem(1L, 1001L, false);

        List<CartEntity> selected = cartService.getSelectedItems(1L);
        assertEquals(1, selected.size());
        assertEquals(1000L, selected.get(0).getSkuId());
    }

    @Test
    @DisplayName("clearCart — 清空用户购物车")
    void shouldClearCart() {
        cartService.addItem(1L, addReq(1000L, 1));
        cartService.addItem(1L, addReq(1001L, 1));
        cartService.clearCart(1L);
        assertTrue(cartService.lambdaQuery().eq(CartEntity::getUserId, 1L).list().isEmpty());
    }
}
