package org.icedamericanomall.item.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.item.adapter.request.FreightTemplateSaveReq;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.app.FreightTemplateManager;
import org.junit.jupiter.api.AfterEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FreightTemplateController 适配层接线测试（F2）—— 商家身份注入与「无登录身份 fail-closed」。
 *
 * <p>锁死两点：① 商家身份取登录态（请求体不承载 sellerId）；② 无登录身份时拒绝，
 * 不把「匿名」静默当作「sellerId = null」（{@code /api/item/**} 的 GET 在网关属匿名放行路径）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreightTemplateController — 商家身份与 fail-closed 守卫")
class FreightTemplateControllerTest {

    @Mock private FreightTemplateManager freightTemplateManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 注册全局异常处理器：standalone 环境不会自动装配 ia-common 的 @RestControllerAdvice
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FreightTemplateController(freightTemplateManager))
                .setControllerAdvice(new org.icedamericanomall.common.aspect.GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("FT1 create — 商家身份来自登录态并透传到领域服务")
    void shouldUseLoginSellerId_whenCreating() throws Exception {
        UserContext.setUser(UserInfo.builder().userId(5L).build());
        FreightTemplateVO vo = new FreightTemplateVO();
        vo.setId(1L);
        when(freightTemplateManager.create(eq(5L), any(FreightTemplateSaveReq.class))).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/item/freight-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateName\":\"全场满99包邮\",\"fee\":800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));

        verify(freightTemplateManager).create(eq(5L), any(FreightTemplateSaveReq.class));
    }

    @Test
    @DisplayName("FT3 list — 返回当前商家模板列表")
    void shouldListCurrentSellerTemplates() throws Exception {
        UserContext.setUser(UserInfo.builder().userId(5L).build());
        FreightTemplateVO vo = new FreightTemplateVO();
        vo.setId(1L);
        when(freightTemplateManager.listBySeller(5L)).thenReturn(List.of(vo));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/freight-template/list"))
                .andExpect(status().isOk());

        verify(freightTemplateManager).listBySeller(5L);
    }

    @Test
    @DisplayName("无登录身份（匿名 GET）— 拒绝而非按 sellerId=null 静默查询")
    void shouldReject_whenAnonymous() throws Exception {
        // UserContext 未设置 → getUserId() 为 null
        mockMvc.perform(MockMvcRequestBuilders.get("/api/item/freight-template/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FT4 delete — 透传当前商家身份做归属校验")
    void shouldPassSellerId_whenDeleting() throws Exception {
        UserContext.setUser(UserInfo.builder().userId(5L).build());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/item/freight-template/9"))
                .andExpect(status().isOk());

        verify(freightTemplateManager).delete(9L, 5L);
    }

    /**
     * 批次 ① 状态码归位：{@code FREIGHT_TEMPLATE_NOT_FOUND(3041)} 由 200 改为 <b>404</b>。
     *
     * <p><b>改前必失败</b>：删除一个不存在（或不属于本店）的模板，改造前返回
     * <b>HTTP 200 + body code 3041</b>；现返回 404，且业务码仍是 3041
     * （前端靠它提示「运费模板不存在」，而 1103/1101 分别是「静态资源」「参数错」）。
     */
    @Test
    @DisplayName("FT4 delete — 模板不存在/非本店 → 404 + 3041（不再是 200）")
    void shouldReturn404_whenTemplateMissingOnDelete() throws Exception {
        UserContext.setUser(UserInfo.builder().userId(5L).build());
        org.mockito.Mockito.doThrow(new org.icedamericanomall.common.exception.BizException(
                        org.icedamericanomall.common.enums.ErrorCode.FREIGHT_TEMPLATE_NOT_FOUND,
                        "运费模板不存在"))
                .when(freightTemplateManager).delete(9L, 5L);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/item/freight-template/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3041))
                .andExpect(jsonPath("$.msg").value("运费模板不存在"));
    }
}
