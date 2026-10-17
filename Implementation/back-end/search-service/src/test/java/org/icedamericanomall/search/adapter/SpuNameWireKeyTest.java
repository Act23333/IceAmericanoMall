package org.icedamericanomall.search.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.icedamericanomall.search.adapter.request.SpuVectorUpsertReq;
import org.icedamericanomall.search.adapter.response.SpuSearchVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * search-service 侧「SPU 名线上键」守卫（契约 §1.8④，2026-10-18「甲b」）——
 * 对外检索报文（SE1）与内部摄取入参（I47）的名称键<b>一律 {@code spu_name}</b>。
 *
 * <p><b>为何两端都要断言</b>：
 * <ul>
 *   <li><b>出方向（SE1 {@code SpuSearchVO}）</b>：与 item 侧 {@code SpuVO.spuName} 同键，
 *       否则前端在「搜索页 / 详情页」两个来源上要写两套取值（其中一处必然静默 {@code undefined}）；</li>
 *   <li><b>入方向（I47 {@code SpuVectorUpsertReq}）</b>：发送端 {@code SpuVectorUpsertCO.spuName}
 *       与本 record <b>必须同批改名</b> —— Jackson 忽略未知键，只改一侧时 {@code spuName}
 *       在接收侧为 {@code null}，随后 {@code @NotBlank} 让摄取请求<b>整体 400</b>，
 *       即「商品编辑后向量永久不更新」的静默功能失效。</li>
 * </ul>
 *
 * <p><b>命名策略对齐生产</b>：{@code search-service application.yml} 声明 {@code SNAKE_CASE}，
 * 故用同一策略的 {@link ObjectMapper}；用默认 camelCase 断言会漏掉本批的全部风险。
 */
@DisplayName("search-service 线格式守卫 —— SE1 / I47 的名称键为 spu_name")
class SpuNameWireKeyTest {

    private static final String SPU_NAME = "无线蓝牙耳机";

    /**
     * 与生产同口径的 ObjectMapper：{@code SNAKE_CASE} + <b>忽略未知键</b>。
     *
     * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES=false} 是 Spring Boot 的默认值、也是本仓
     * 各服务运行时的实际行为 —— 它正是本批「单侧改名静默落 {@code null}」的机理，
     * 故必须原样复现：若用 Jackson 原生默认（true）配，旧键会直接抛异常，
     * 反而看不到真实故障形态（静默 {@code null} + {@code @NotBlank} 打回 400）。
     */
    private final ObjectMapper wireMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("SE1 SpuSearchVO — 键 spu_name，无 name")
    void shouldRenderSpuName_whenSearchVO() throws Exception {
        SpuSearchVO vo = new SpuSearchVO();
        vo.setSpuId("0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        vo.setSpuName(SPU_NAME);

        JsonNode json = wireMapper.readTree(wireMapper.writeValueAsString(vo));

        assertEquals(SPU_NAME, json.get("spu_name").asText(), "SE1 名称键必须是 spu_name（§1.8④）");
        assertFalse(json.has("name"), "同名旧键不得残留: " + json);
    }

    @Test
    @DisplayName("I47 SpuVectorUpsertReq — 入参键 spu_name 绑定成功；旧键 name 不绑定（防单侧残留）")
    void shouldBindSpuNameKey_whenVectorUpsertReq() throws Exception {
        SpuVectorUpsertReq req = wireMapper.readValue(
                "{\"spu_id\":\"0f1e2d3c4b5a69788796a5b4c3d2e1f0\",\"spu_name\":\"" + SPU_NAME + "\"}",
                SpuVectorUpsertReq.class);

        assertEquals(SPU_NAME, req.spuName(), "I47 线上键为 spu_name（契约 I47 行）");
        assertEquals("0f1e2d3c4b5a69788796a5b4c3d2e1f0", req.spuId());

        // 反向断言（本批「两端必须同一次改动」的护栏）：旧键 name 不再被绑定 ——
        // 这正是「发送侧已改名、接收侧未改」时发生的事：spuName 恒 null，@NotBlank 打回 400。
        SpuVectorUpsertReq legacy = wireMapper.readValue(
                "{\"spu_id\":\"0f1e2d3c4b5a69788796a5b4c3d2e1f0\",\"name\":\"" + SPU_NAME + "\"}",
                SpuVectorUpsertReq.class);
        assertNull(legacy.spuName(), "旧键 name 必须不再绑定（否则会出现第三种口径）");
    }

    @Test
    @DisplayName("结构自证：两个载体类声明的是 spuName 字段（防守卫自身失效）")
    void shouldDeclareSpuNameField() throws Exception {
        assertEquals(String.class, SpuSearchVO.class.getDeclaredField("spuName").getType());
        // record 组件的反射面与字段面一致
        assertEquals(String.class, SpuVectorUpsertReq.class.getDeclaredField("spuName").getType());
        assertFalse(java.util.Arrays.stream(SpuSearchVO.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("name")), "SpuSearchVO 不得保留 name 字段");
    }
}
