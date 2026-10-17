package org.icedamericanomall.item.adapter.admin;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.item.app.HomeConfigManager;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首页装修配置更新（{@code PUT /api/admin/home/{id}}）的存在性承载 —— 批次 ① 状态码归位。
 *
 * <p><b>改前必失败</b>：改造前 {@code HOME_CONFIG_NOT_FOUND(3017)} 配 {@code HttpStatus.OK}，
 * 管理端更新一个已被删除的配置会拿到 <b>HTTP 200 + body code 3017</b>（看起来像「成功」，
 * 只有把 body 解开才知道失败）；现返回 <b>404</b>。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminHomeController — 首页配置不存在 404/3017")
class AdminHomeControllerNotFoundTest {

    private static final String VALID_BODY = """
            {"slot":"BANNER","type":1,"title":"首页轮播"}
            """;

    @Mock
    private HomeConfigManager homeConfigManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminHomeController(homeConfigManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("更新已删除的配置 → 404 + 3017（不再是 200）")
    void shouldReturn404_whenHomeConfigMissing() throws Exception {
        when(homeConfigManager.updateConfig(anyLong(), any()))
                .thenThrow(new BizException(ErrorCode.HOME_CONFIG_NOT_FOUND));

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/home/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3017))
                .andExpect(jsonPath("$.msg").value("首页配置不存在"));
    }
}
