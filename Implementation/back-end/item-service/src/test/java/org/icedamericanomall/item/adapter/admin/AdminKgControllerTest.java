package org.icedamericanomall.item.adapter.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.item.adapter.response.PairingRuleVO;
import org.icedamericanomall.item.app.kg.KgAdminManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminKgController 适配层接线测试（F15 AD61-64）—— 委托与审计注解锁死：
 * ① 分页/创建/编辑/重建正确委托 {@code KgAdminManager}；② 全部写操作携带
 * {@code @OperationLog} 审计注解（ADR-032 管道一，US-KG-7）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminKgController — 治理端委托与审计注解")
class AdminKgControllerTest {

    @Mock
    private KgAdminManager kgAdminManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminKgController(kgAdminManager)).build();
    }

    @Test
    @DisplayName("AD61 page — 查询参数透传并返回规则分页")
    void shouldDelegatePage_whenFiltersGiven() throws Exception {
        Page<PairingRuleVO> page = new Page<>(1, 20);
        PairingRuleVO vo = new PairingRuleVO();
        vo.setId(1L);
        vo.setSrcCategoryId(10L);
        vo.setDstCategoryId(20L);
        page.setRecords(List.of(vo));
        when(kgAdminManager.page(10L, 20L, 1, 2, 5)).thenReturn(page);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/kg/rule/page")
                        .param("srcCategoryId", "10")
                        .param("dstCategoryId", "20")
                        .param("status", "1")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(1));

        verify(kgAdminManager).page(10L, 20L, 1, 2, 5);
    }

    @Test
    @DisplayName("AD62 create — 请求体校验后委托；srcCategoryId 缺失 → 400")
    void shouldValidateAndDelegate_whenCreate() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/kg/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"srcCategoryId\":10,\"dstCategoryId\":20,\"weight\":8}"))
                .andExpect(status().isOk());

        verify(kgAdminManager).create(any());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/kg/rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dstCategoryId\":20}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AD63 update — null-safe 编辑委托（路径 id + 局部字段）")
    void shouldDelegateUpdate_whenPatchBody() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/kg/rule/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());

        verify(kgAdminManager).update(eq(7L), any());
    }

    @Test
    @DisplayName("AD64 rebuild — 返回重建边数")
    void shouldReturnWrittenCount_whenRebuild() throws Exception {
        when(kgAdminManager.rebuild()).thenReturn(42);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/kg/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(42));

        verify(kgAdminManager).rebuild();
    }

    @Test
    @DisplayName("审计注解（US-KG-7）— create/update/rebuild 全部携带 @OperationLog")
    void shouldCarryOperationLog_onAllWriteOperations() throws Exception {
        assertTrue(AdminKgController.class.getDeclaredMethod("create", anyReqClass())
                .isAnnotationPresent(OperationLog.class));
        assertTrue(AdminKgController.class.getDeclaredMethod("update", Long.class, anyUpdateReqClass())
                .isAnnotationPresent(OperationLog.class));
        assertTrue(AdminKgController.class.getDeclaredMethod("rebuild")
                .isAnnotationPresent(OperationLog.class));
    }

    private Class<?> anyReqClass() {
        return org.icedamericanomall.item.adapter.request.PairingRuleReq.class;
    }

    private Class<?> anyUpdateReqClass() {
        return org.icedamericanomall.item.adapter.request.PairingRuleUpdateReq.class;
    }
}
