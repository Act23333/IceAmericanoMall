package org.icedamericanomall.notification.adapter.web;

import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.notification.adapter.response.UnreadCountVO;
import org.icedamericanomall.notification.app.InAppMessageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 未读计数端点测试（U29 {@code GET /api/notifications/unread-count}）——
 * 批次 D：{@code Result<Map<String, Integer>>} → {@code Result<UnreadCountVO>}。
 *
 * <p><b>无 BREAKING 的证据</b>：线上键仍是单字 {@code unread}，故前端
 * {@code packages/api/types} 的 {@code UnreadCountVO.unread} 与小程序
 * {@code pages/profile} 的 {@code data.unread} 读取点<b>无需改动</b>；
 * 本测试同时钉住「0 是合法业务态」（未读数 0 时仍有完整报文，不是空 body / null data）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController — 未读计数（U29）类型化 VO")
class NotificationUnreadCountTest {

    private static final Long USER_ID = 9L;

    @Mock
    private InAppMessageManager inAppMessageManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(inAppMessageManager))
                .build();
        UserContext.setUser(UserInfo.builder().userId(USER_ID).build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    @DisplayName("unread-count — 委托 countUnread(当前用户)，data.unread 原样透出")
    void shouldExposeUnreadKey_whenUnreadCount() throws Exception {
        when(inAppMessageManager.countUnread(USER_ID)).thenReturn(3);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(3));

        verify(inAppMessageManager).countUnread(eq(USER_ID));
    }

    @Test
    @DisplayName("unread-count — 无未读 → data.unread = 0（合法业务态，不返回空 data）")
    void shouldExposeZero_whenNoUnread() throws Exception {
        when(inAppMessageManager.countUnread(USER_ID)).thenReturn(0);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(0));
    }

    @Test
    @DisplayName("UnreadCountVO.of — 静态工厂与既有 Map 键同形（装配单一出口）")
    void shouldBuildViaStaticFactory() {
        UnreadCountVO vo = UnreadCountVO.of(7);

        org.junit.jupiter.api.Assertions.assertEquals(7, vo.getUnread());
    }
}
