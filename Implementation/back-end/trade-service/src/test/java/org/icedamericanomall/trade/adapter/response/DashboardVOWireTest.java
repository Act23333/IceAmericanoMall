package org.icedamericanomall.trade.adapter.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批次 D 守卫：{@code Result<Map>} → 类型化 VO 后的**线上键**必须是 snake_case。
 *
 * <p><b>为什么需要本测试</b>：全仓 MockMvc 测试都走 {@code standaloneSetup}（不加载
 * {@code application.yml}），因此 {@code spring.jackson.property-naming-strategy: SNAKE_CASE}
 * 在测试里**不生效** —— 「换成 VO 后线上键变成 camelCase」这种缺陷在其它测试里完全不可见。
 * 本类显式用 {@code PropertyNamingStrategies.SNAKE_CASE} 建 ObjectMapper，直接断言报文键集合。
 *
 * <p><b>为什么这很关键</b>：改造前这几个端点是**Map 字面键**（{@code todayOrderCount} /
 * {@code sellerId} 等 camelCase），换成 VO 后由字段名 + 命名策略产出 snake_case
 * （{@code today_order_count} / {@code seller_id}）—— 与契约 S10/T22/AD11/AD13/AD15 声明一致，
 * 但**对任何既有消费方都是线上键 BREAKING**。本测试把「新键」钉死，防止再改回 camelCase，
 * 也防止有人给字段加 {@code @JsonProperty} 把它钉成旧键。
 */
@DisplayName("批次 D — VO 线上键（SNAKE_CASE）守卫")
class DashboardVOWireTest {

    /** 与各服务 application.yml 同口径（spring.jackson.property-naming-strategy: SNAKE_CASE）。 */
    private final ObjectMapper snakeCaseMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("SellerDashboardVO — 键为 today_order_count / pending_ship_count / monthly_revenue")
    void shouldRenderSnakeCaseKeys_whenSellerDashboardSerialized() throws Exception {
        SellerDashboardVO vo = new SellerDashboardVO();
        vo.setTodayOrderCount(3L);
        vo.setPendingShipCount(2L);
        vo.setMonthlyRevenue(12800);

        Map<String, Object> wire = readMap(snakeCaseMapper.writeValueAsString(vo));

        assertEquals(new TreeSet<>(List.of("today_order_count", "pending_ship_count", "monthly_revenue")),
                new TreeSet<>(wire.keySet()));
        assertEquals(12800, wire.get("monthly_revenue"));
        assertFalse(wire.containsKey("todayOrderCount"), "旧 Map 字面键 todayOrderCount 必须已消失");
    }

    @Test
    @DisplayName("AdminDashboardVO — 6 个键全为 snake_case，且不含契约多写的两个臆造指标")
    void shouldRenderSnakeCaseKeys_whenAdminDashboardSerialized() throws Exception {
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(12580L);
        vo.setTotalOrders(45620L);
        vo.setCompletedOrders(1000L);
        vo.setTotalRevenue(892450000);
        vo.setTodayOrders(356L);
        vo.setTodayRevenue(5680000);

        Map<String, Object> wire = readMap(snakeCaseMapper.writeValueAsString(vo));

        assertEquals(new TreeSet<>(List.of("total_users", "total_orders", "completed_orders",
                        "total_revenue", "today_orders", "today_revenue")),
                new TreeSet<>(wire.keySet()));
        assertEquals(1000, wire.get("completed_orders"));
        assertFalse(wire.containsKey("today_new_users"), "契约样例的 today_new_users 代码从未产出");
        assertFalse(wire.containsKey("pending_seller_count"), "契约样例的 pending_seller_count 代码从未产出");
    }

    @Test
    @DisplayName("SpuStatVO — 元素键为 spu_name / quantity / revenue（§1.8④ 钉死 spu_name）")
    void shouldRenderSpuNameKey_whenSpuStatSerialized() throws Exception {
        SpuStatVO vo = new SpuStatVO();
        vo.setSpuName("冰美式·大杯");
        vo.setQuantity(7);
        vo.setRevenue(25600);

        Map<String, Object> wire = readMap(snakeCaseMapper.writeValueAsString(vo));

        assertEquals(new TreeSet<>(List.of("spu_name", "quantity", "revenue")), new TreeSet<>(wire.keySet()));
        assertEquals("冰美式·大杯", wire.get("spu_name"));
        assertEquals(25600, wire.get("revenue"));
        assertFalse(wire.containsKey("spuName"));
        assertFalse(wire.containsKey("productName"));
    }

    @Test
    @DisplayName("OrderTrendVO / SalesByDateVO — 元素键为 date / orders / revenue")
    void shouldRenderDateKeys_whenTrendAndSalesSerialized() throws Exception {
        OrderTrendVO trend = new OrderTrendVO();
        trend.setDate("2026-10-18");
        trend.setOrders(12L);
        trend.setRevenue(34000);

        SalesByDateVO sales = new SalesByDateVO();
        sales.setDate("2026-10-18");
        sales.setOrders(12L);
        sales.setRevenue(34000);

        Set<String> expected = new TreeSet<>(List.of("date", "orders", "revenue"));
        assertEquals(expected, new TreeSet<>(readMap(snakeCaseMapper.writeValueAsString(trend)).keySet()));
        assertEquals(expected, new TreeSet<>(readMap(snakeCaseMapper.writeValueAsString(sales)).keySet()));
    }

    @Test
    @DisplayName("SellerBalanceVO — 键为 seller_id / balance（旧实现发的是 sellerId）")
    void shouldRenderSellerIdSnake_whenSellerBalanceSerialized() throws Exception {
        SellerBalanceVO vo = new SellerBalanceVO();
        vo.setSellerId(9L);
        vo.setBalance(1280000);

        Map<String, Object> wire = readMap(snakeCaseMapper.writeValueAsString(vo));

        assertEquals(new TreeSet<>(List.of("seller_id", "balance")), new TreeSet<>(wire.keySet()));
        assertEquals(9, wire.get("seller_id"));
        assertEquals(1280000, wire.get("balance"));
    }

    @Test
    @DisplayName("六个 VO 都不得含 Map 逃生口字段（弱类型回退面为零）")
    void shouldNotExposeWeakMapField_whenAnyDashboardVODeclared() {
        for (Class<?> type : List.of(SellerDashboardVO.class, SpuStatVO.class, AdminDashboardVO.class,
                OrderTrendVO.class, SalesByDateVO.class, SellerBalanceVO.class)) {
            for (var field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                assertFalse(Map.class.isAssignableFrom(fieldType),
                        type.getSimpleName() + " 不得存在 Map 类型的兜底字段: " + field.getName());
                assertTrue(!fieldType.equals(Object.class),
                        type.getSimpleName() + " 不得存在 Object 类型的兜底字段: " + field.getName());
            }
        }
    }

    private static Map<String, Object> readMap(String json) throws Exception {
        return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }
}
