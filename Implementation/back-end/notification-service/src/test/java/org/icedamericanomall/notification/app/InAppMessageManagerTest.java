package org.icedamericanomall.notification.app;

import org.icedamericanomall.notification.domain.model.inapp.InAppMessage;
import org.icedamericanomall.notification.domain.port.EmailChannelPort;
import org.icedamericanomall.notification.domain.port.SmsChannelPort;
import org.icedamericanomall.notification.domain.repository.InAppMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 站内信编排单元测试 —— 落库保存与 self-send 忽略语义。
 *
 * <p>由原 {@code NotificationManagerTest} 的站内信用例随用例族拆分迁移而来，断言口径不变：
 * 站内信只落库、不经短信/邮件通道投递。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通信 — 站内信编排")
class InAppMessageManagerTest {

    @Mock
    private InAppMessageRepository inAppMessageRepository;

    /** 通道端口不属本用例族协作者：保留断言以固化「站内信不经通道投递」的不变量。 */
    @Mock
    private SmsChannelPort smsChannelPort;

    @Mock
    private EmailChannelPort emailChannelPort;

    @InjectMocks
    private InAppMessageManager inAppMessageManager;

    @Test
    @DisplayName("编排 — IN_APP：落库保存（不经通道投递），self-send 忽略")
    void shouldSaveInAppMessage_andSkipSelfSend() {
        InAppMessage message = InAppMessage.unread(1001L, 2002L, "商家", null,
                "REPLY", "回复", "内容", "/product/1");

        inAppMessageManager.sendInAppMessage(message);

        verify(inAppMessageRepository).save(message);
        verifyNoInteractions(smsChannelPort);
        verifyNoInteractions(emailChannelPort);

        // self-send（sender == recipient）→ 忽略，不落库
        inAppMessageManager.sendInAppMessage(InAppMessage.unread(2002L, 2002L, null, null,
                "REPLY", "回复", "内容", "/product/1"));
        verify(inAppMessageRepository).save(message); // 仍只有第一次的保存
    }
}
