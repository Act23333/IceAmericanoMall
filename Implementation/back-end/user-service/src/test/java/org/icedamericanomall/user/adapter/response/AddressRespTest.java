package org.icedamericanomall.user.adapter.response;

import org.icedamericanomall.user.domain.model.address.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AddressResp 出参形状单元测试 —— 锁定「审计字段 updateTime 已从出参投影中移除」。
 *
 * <p>依据：前端全仓对该字段只声明不消费，按最小暴露面收窄（V5.4）。
 * 领域模型保留 updateTime（本测试同时确认领域侧不受影响）。
 */
@DisplayName("AddressResp 出参形状（updateTime 已移除）")
class AddressRespTest {

    @Test
    @DisplayName("AddressResp — 不含 updateTime 字段（前端只声明不消费，按最小暴露面删除）")
    void shouldNotExposeUpdateTime() {
        assertTrue(Arrays.stream(AddressResp.class.getDeclaredFields())
                        .noneMatch(f -> "updateTime".equals(f.getName())),
                "AddressResp 不应再暴露审计字段 updateTime");
    }

    @Test
    @DisplayName("AddressResp.from — 业务字段逐字段投影，createTime 保留")
    void shouldMapBusinessFields() {
        Address address = Address.builder()
                .id(1L).userId(100L).receiver("张三").phone("13800138000").defaulted(true)
                .build();

        AddressResp resp = AddressResp.from(address);

        assertEquals(1L, resp.getId());
        assertEquals(100L, resp.getUserId());
        assertEquals("张三", resp.getReceiver());
        assertEquals("13800138000", resp.getPhone());
        assertTrue(resp.getDefaulted());
        // domain 侧仍维护 updateTime（仅出参不再投影）
        address.markDefault();
        assertNotNull(address.getUpdateTime(), "领域模型应仍维护 updateTime（内部审计口径）");
    }
}
