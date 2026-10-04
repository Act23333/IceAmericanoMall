package org.icedamericanomall.ai.adapter.assembler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.icedamericanomall.ai.adapter.request.ChatSendReq;
import org.icedamericanomall.ai.adapter.response.ChatConversationVO;
import org.icedamericanomall.ai.adapter.response.ChatMessageVO;
import org.icedamericanomall.ai.app.chat.ChatPrincipal;
import org.icedamericanomall.ai.app.command.SendChatMessageCommand;
import org.icedamericanomall.ai.app.dto.ChatConversationDTO;
import org.icedamericanomall.ai.app.dto.ChatMessageDTO;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ChatMessageConvertor 单元测试 —— 身份只认 STOMP 会话（报文中伪造的身份字段一律忽略），
 * 以及应用层 DTO → 视图对象的字段装配。
 */
class ChatMessageConvertorTest {

    private final ChatMessageConvertor convertor = new ChatMessageConvertor();

    @Test
    @DisplayName("身份伪造 — 报文里的 senderId/senderRole 被忽略，取会话身份")
    void shouldIgnoreForgedSenderId_whenConverting() throws Exception {
        // 与 Spring Boot 默认一致：未知字段不报错（伪造字段被静默丢弃）
        ObjectMapper objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ChatSendReq req = objectMapper.readValue("""
                {"conversationId":"buyer_1_seller_2_product_9","senderId":999,
                 "senderRole":"SELLER","receiverId":2,"content":"hi"}
                """, ChatSendReq.class);

        SendChatMessageCommand command = convertor.toCommand(req, new ChatPrincipal(1L, Set.of("ROLE_BUYER")));

        assertEquals(1L, command.senderId());
        assertEquals(ChatPrincipal.SENDER_ROLE_BUYER, command.senderRole());
        assertEquals(2L, command.receiverId());
        assertEquals("hi", command.content());
    }

    @Test
    @DisplayName("会话身份为卖家 → 发送者角色 SELLER")
    void shouldDeriveSellerRole_whenSessionHasSellerRole() {
        ChatSendReq req = request();

        SendChatMessageCommand command = convertor.toCommand(req, new ChatPrincipal(8L, Set.of("ROLE_SELLER")));

        assertEquals(8L, command.senderId());
        assertEquals(ChatPrincipal.SENDER_ROLE_SELLER, command.senderRole());
    }

    @Test
    @DisplayName("会话身份缺失 → UNAUTHORIZED，拒绝发送")
    void shouldThrowUnauthorized_whenPrincipalMissing() {
        assertThrows(UnauthorizedException.class, () -> convertor.toCommand(request(), null));
    }

    @Test
    @DisplayName("消息 DTO → VO — 字段逐一保留（JSON 形状不变）")
    void shouldMapAllFields_whenConvertingMessage() {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setId(9L);
        dto.setMessageId("m-1");
        dto.setConversationId("c-1");
        dto.setSenderId(1L);
        dto.setSenderRole(ChatPrincipal.SENDER_ROLE_BUYER);
        dto.setReceiverId(2L);
        dto.setContent("在吗");
        dto.setContentType("TEXT");
        dto.setExtra("{\"k\":1}");
        dto.setIsRead(0);
        dto.setIsAiGenerated(0);
        dto.setCreateTime(LocalDateTime.now());

        ChatMessageVO vo = convertor.toVO(dto);

        assertEquals(dto.getId(), vo.getId());
        assertEquals(dto.getMessageId(), vo.getMessageId());
        assertEquals(dto.getConversationId(), vo.getConversationId());
        assertEquals(dto.getSenderId(), vo.getSenderId());
        assertEquals(dto.getSenderRole(), vo.getSenderRole());
        assertEquals(dto.getReceiverId(), vo.getReceiverId());
        assertEquals(dto.getContent(), vo.getContent());
        assertEquals(dto.getContentType(), vo.getContentType());
        assertEquals(dto.getExtra(), vo.getExtra());
        assertEquals(dto.getIsRead(), vo.getIsRead());
        assertEquals(dto.getIsAiGenerated(), vo.getIsAiGenerated());
        assertEquals(dto.getCreateTime(), vo.getCreateTime());
    }

    @Test
    @DisplayName("会话摘要 DTO → VO — 只暴露 4 个对外字段（不含内部 messageCount）")
    void shouldExposeOnlyExternalFields_whenConvertingConversation() {
        ChatConversationDTO dto = new ChatConversationDTO();
        dto.setConversationId("c-1");
        dto.setLastMessage("好的，今天发货");
        dto.setLastTime(LocalDateTime.now());
        dto.setUnreadCount(2);
        dto.setMessageCount(37);

        ChatConversationVO vo = convertor.toConversationVO(dto);
        List<ChatConversationVO> list = convertor.toConversationVOList(List.of(dto));

        assertEquals("c-1", vo.getConversationId());
        assertEquals("好的，今天发货", vo.getLastMessage());
        assertEquals(dto.getLastTime(), vo.getLastTime());
        assertEquals(2, vo.getUnreadCount());
        assertEquals(1, list.size());
        assertEquals(vo.getConversationId(), list.get(0).getConversationId());
    }

    @Test
    @DisplayName("空入参 — 返回 null / 空列表（不抛异常）")
    void shouldTolerateNullInput() {
        assertNull(convertor.toVO((ChatMessageDTO) null));
        assertNull(convertor.toConversationVO(null));
        assertEquals(List.of(), convertor.toVOList(null));
        assertEquals(List.of(), convertor.toConversationVOList(null));
    }

    private static ChatSendReq request() {
        ChatSendReq req = new ChatSendReq();
        req.setConversationId("buyer_1_seller_2_product_9");
        req.setReceiverId(2L);
        req.setContent("在吗");
        return req;
    }
}
