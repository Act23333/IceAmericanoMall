package org.icedamericanomall.item.adapter.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPU 名线上键守卫（契约 §1.8④，2026-10-18「甲b」）—— 商品详情 / 推荐卡 / 笔记商品卡
 * 三种报文里的「某个 SPU 的名字」键<b>一律 {@code spu_name}</b>，且<b>不得残留 {@code name}</b>。
 *
 * <p><b>为什么需要它（本批的成因）</b>：裁决前同一概念有两个线上键 —— SPU 自身 VO 用 {@code name}，
 * 回填旁路 VO（{@code SkuVO}/{@code CartItemVO}/{@code FavoriteVO}/{@code FlashSaleVO}/
 * {@code OrderItemVO}）用 {@code spu_name}。前端按其中任一键取值，都会在另一侧
 * <b>静默变 {@code undefined}</b>（不报错、只是显示空白）—— 项目里多数「数据不显示」缺陷即此成因。
 * 既有 6 道 CI 闸只查<b>路径 / 错误码 / 查询参数 / 端点漂移</b>，<b>没有一道看字段级一致性</b>，
 * 所以这里必须站在<b>序列化接缝</b>上断言，并同时断言旧键<b>不存在</b>
 * （只断言「有 spu_name」会漏掉「两个键并存」这个真正的坑）。
 *
 * <p><b>命名策略对齐生产</b>：{@code application.yml} 声明 {@code SNAKE_CASE}，
 * 故这里用同一策略建 {@link ObjectMapper}；否则断言的是 Java 字段名而非线上键名。
 */
@DisplayName("SPU 名线上键守卫 —— P2 / P27 / P35 / P30 一律 spu_name，且不残留 name")
class SpuNameWireKeyTest {

    private static final String SPU_NAME = "手工陶瓷茶杯";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("SpuVO（P1/P2/P38）— 键 spu_name，无 name")
    void shouldUseSpuNameKey_whenSpuVO() throws Exception {
        SpuVO vo = new SpuVO();
        vo.setSpuId("0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        vo.setSpuName(SPU_NAME);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(vo));

        assertEquals(SPU_NAME, json.get("spu_name").asText());
        assertFalse(json.has("name"), "同名旧键不得残留（否则前端按 name 读会静默 undefined）: " + keys(json));
    }

    @Test
    @DisplayName("RecommendSpuVO（P27 相似卡 / P35 搭配卡）— 卡内键 spu_name，无 name")
    void shouldUseSpuNameKey_whenRecommendSpuVO() throws Exception {
        RecommendSpuVO card = new RecommendSpuVO();
        card.setSpuId("112233445566778899aabbccddeeff00");
        card.setSpuName(SPU_NAME);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(card));

        assertEquals(SPU_NAME, json.get("spu_name").asText());
        assertFalse(json.has("name"), "卡内旧键不得残留: " + keys(json));
    }

    @Test
    @DisplayName("NoteDetailVO.spus[]（P30）— 关联商品卡键 spu_name，无 name")
    void shouldUseSpuNameKey_whenNoteSpuSummary() throws Exception {
        NoteDetailVO.SpuSummaryVO summary = new NoteDetailVO.SpuSummaryVO();
        summary.setSpuId("112233445566778899aabbccddeeff00");
        summary.setSpuName(SPU_NAME);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(summary));

        assertEquals(SPU_NAME, json.get("spu_name").asText());
        assertFalse(json.has("name"), "卡内旧键不得残留: " + keys(json));
    }

    /** 递归收集 JSON 全部键（失败信息里给出实际键集，便于定位）。 */
    private static Set<String> keys(JsonNode node) {
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

    @Test
    @DisplayName("结构自证：三个载体类的字段名确实是 spuName（防「测试类自己改名」后守卫假绿）")
    void shouldDeclareSpuNameField() throws Exception {
        assertTrue(hasField(SpuVO.class, "spuName"));
        assertTrue(hasField(RecommendSpuVO.class, "spuName"));
        assertTrue(hasField(NoteDetailVO.SpuSummaryVO.class, "spuName"));
        assertFalse(hasField(SpuVO.class, "name"));
        assertFalse(hasField(RecommendSpuVO.class, "name"));
        assertFalse(hasField(NoteDetailVO.SpuSummaryVO.class, "name"));
    }

    private static boolean hasField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
