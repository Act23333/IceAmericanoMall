package org.icedamericanomall.item.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.icedamericanomall.item.adapter.assembler.SkuConverterImpl;
import org.icedamericanomall.item.adapter.assembler.SpuConverterImpl;
import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.domain.enums.SpuStatusEnum;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.icedamericanomall.item.domain.port.ShortCodeResolver;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * P38 与 P2 的线格式<b>逐字段同形</b>守卫（ADR-063 §守卫 5，批次 B7b）—— 对同一商品，
 * 两条端点路径产出的 {@code SpuVO} 序列化结果必须<b>完全一致</b>，且<b>都不含 {@code short_code}</b>。
 *
 * <p><b>为什么必须有这条守卫</b>：契约要求 P38 复用 P2 的装配（不另建第二套逻辑），但「复用」
 * 只写在注释里，靠人记住无法防漂移 —— 一旦有人为 P38 单独加字段（尤其 {@code short_code}），
 * 短码就从「承载层别名」被升格成「对外标识」，前端会缓存/回链它，§1.9⑧ 的禁令当场失效。
 * 本测试站在<b>序列化接缝</b>上断言：两次响应的 JSON 树 diff 为空，且键集不含短码。
 *
 * <p><b>命名策略对齐生产</b>：{@code application.yml} 声明 {@code SNAKE_CASE}，
 * 故这里用同一策略建 {@link ObjectMapper}，否则断言的是「Java 字段名」而不是「线上键名」。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpuVO 线格式守卫 —— P38 与 P2 逐字段同形 / 短码不进报文")
class SpuDetailWireShapeTest {

    private static final String SPU_BUSINESS_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SHORT_CODE = "8f3a1c9d20";
    private static final Long SPU_TECHNICAL_ID = 1L;

    @Mock
    private SpuRepository spuRepository;

    @Mock
    private SkuRepository skuRepository;

    @Mock
    private ShortCodeResolver shortCodeResolver;

    @Mock
    private org.icedamericanomall.client.PromotionClient promotionClient;

    private SpuManager spuManager;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @BeforeEach
    void setUp() {
        spuManager = new SpuManager(spuRepository, skuRepository,
                new SpuConverterImpl(), new SkuConverterImpl(),
                org.mockito.Mockito.mock(FreightTemplateManager.class),
                new SkuManager(skuRepository, new SkuConverterImpl(),
                        new org.icedamericanomall.item.app.port.SkuSpuDisplayQuery() {
                            @Override
                            public java.util.Map<Long, org.icedamericanomall.item.app.dto.SkuSpuDisplay> findBySkuIds(
                                    java.util.Collection<Long> skuIds) {
                                return java.util.Map.of();
                            }

                            @Override
                            public java.util.Map<String, org.icedamericanomall.item.app.dto.SkuSpuDisplay>
                                    findBySkuBusinessIds(java.util.Collection<String> skuBusinessIds) {
                                return java.util.Map.of();
                            }
                        }),
                org.mockito.Mockito.mock(org.icedamericanomall.client.SpuVectorClient.class),
                org.mockito.Mockito.mock(SpuRecommendManager.class),
                new SpuPromotionManager(promotionClient),
                org.mockito.Mockito.mock(org.icedamericanomall.item.app.kg.KgEdgeIngestManager.class),
                shortCodeResolver);
    }

    /** 同一商品（在售，含两个 SKU）的读模型桩。 */
    private void stubSameSpu() {
        Spu spu = Spu.builder()
                .id(SPU_TECHNICAL_ID).spuId(SPU_BUSINESS_ID).shortCode(SHORT_CODE)
                .sellerId(5L).categoryId(1L).name("手工陶瓷茶杯").mainImage("https://cdn/x.jpg")
                .description("纯手工").brand("冰美精选").soldCount(12).commentCount(3)
                .status(SpuStatusEnum.ON_SHELF.getCode())
                .publishTime(LocalDateTime.of(2026, 10, 1, 10, 0))
                .build();
        when(spuRepository.findByBusinessId(SPU_BUSINESS_ID)).thenReturn(spu);
        when(skuRepository.findBySpuId(SPU_TECHNICAL_ID)).thenReturn(List.of(
                Sku.builder().id(11L).spuId(SPU_TECHNICAL_ID).spec("白瓷").price(12900).status(1).build(),
                Sku.builder().id(12L).spuId(SPU_TECHNICAL_ID).spec("青瓷").price(15900).status(1).build()));
        when(shortCodeResolver.resolve(SHORT_CODE)).thenReturn(SPU_BUSINESS_ID);
    }

    @Test
    @DisplayName("P38 与 P2 对同一商品的响应 JSON 逐字段同形（diff 为空）")
    void shouldBeIdenticalShape() throws Exception {
        stubSameSpu();

        SpuVO byId = spuManager.getSpuDetail(SPU_BUSINESS_ID);
        SpuVO byCode = spuManager.getSpuDetailByCode(SHORT_CODE);

        JsonNode p2 = objectMapper.readTree(objectMapper.writeValueAsString(byId));
        JsonNode p38 = objectMapper.readTree(objectMapper.writeValueAsString(byCode));

        assertEquals(p2, p38, "P38 与 P2 必须逐字段同形（否则前端要为同一资源维护两套解析）");
        assertEquals(keySet(p2), keySet(p38));
    }

    @Test
    @DisplayName("短码不进报文：两次响应的键集都不含 short_code / shortCode")
    void shouldNotLeakShortCode() throws Exception {
        stubSameSpu();

        Set<String> keys = keySet(objectMapper.readTree(
                objectMapper.writeValueAsString(spuManager.getSpuDetailByCode(SHORT_CODE))));

        assertTrue(keys.stream().noneMatch(k -> k.toLowerCase().contains("short")),
                "短码一旦进报文就升格为对外标识（§1.9⑧ 硬约束）: " + keys);
        assertFalse(keys.contains("short_code"));
        assertFalse(keys.contains("shortCode"));
    }

    @Test
    @DisplayName("结构守卫：SpuVO 类上不存在任何 shortCode/short_code 字段（防新增字段绕过 JSON 断言）")
    void shouldHaveNoShortCodeFieldOnVo() {
        List<String> hits = new ArrayList<>();
        for (Field field : SpuVO.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("shortcode") || name.contains("short_code")) {
                hits.add(field.getName());
            }
        }
        assertTrue(hits.isEmpty(), "SpuVO 不得出现短码字段: " + hits);
        // 自证：反射面确实读到了字段（否则本守卫零命中也是假绿）
        assertTrue(Arrays.stream(SpuVO.class.getDeclaredFields()).count() > 5);
    }

    /** 递归收集 JSON 树的全部键（含嵌套对象）。 */
    private static Set<String> keySet(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        collect(node, keys);
        return keys;
    }

    private static void collect(JsonNode node, Set<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                keys.add(name);
                collect(node.get(name), keys);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, keys));
        }
    }
}
