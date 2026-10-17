package org.icedamericanomall.notification.adapter.web;

import org.icedamericanomall.common.aspect.GlobalExceptionHandler;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.notification.app.InAppMessageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站内信已读（{@code PUT /api/notifications/{id}/read}）的存在性承载 —— 批次 ①。
 *
 * <p><b>改前必失败</b>：改造前 {@code NOTIFICATION_NOT_FOUND(3034)} 配 200，
 * 标记一条不存在的站内信返回 <b>HTTP 200 + body code 3034</b>；现返回 <b>404</b>。
 *
 * <p><b>反枚举口径保持不变</b>：该分支同时覆盖「通知不存在」与「通知不属于本人」
 * （归属校验在仓储条件内），两者在 404 下依然<b>完全同形</b>（状态码 + 业务码 + 文案），
 * 没有因为状态码归位而泄漏他人站内信的存在性。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController — 通知不存在 404/3034")
class NotificationControllerNotFoundTest {

    @Mock
    private InAppMessageManager inAppMessageManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(inAppMessageManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("标记已读 — 通知不存在 → 404 + 3034（不再是 200）")
    void shouldReturn404_whenNotificationMissing() throws Exception {
        doThrow(new BizException(ErrorCode.NOTIFICATION_NOT_FOUND, "通知不存在"))
                .when(inAppMessageManager).markRead(anyLong(), any());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/notifications/9/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3034))
                .andExpect(jsonPath("$.msg").value("通知不存在"));
    }

    @Test
    @DisplayName("标记已读 — 通知不属于本人 → 与「不存在」逐字同形（反枚举未被放宽）")
    void shouldMaskOwnershipAsNotFound_whenNotificationOfAnotherUser() throws Exception {
        doThrow(new BizException(ErrorCode.NOTIFICATION_NOT_FOUND, "通知不存在"))
                .when(inAppMessageManager).markRead(anyLong(), any());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/notifications/10/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3034))
                .andExpect(jsonPath("$.msg").value("通知不存在"));
    }
}
