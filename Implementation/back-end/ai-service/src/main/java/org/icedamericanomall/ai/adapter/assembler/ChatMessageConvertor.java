package org.icedamericanomall.ai.adapter.assembler;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.ai.adapter.request.ChatSendReq;
import org.icedamericanomall.ai.adapter.response.ChatConversationVO;
import org.icedamericanomall.ai.adapter.response.ChatMessageVO;
import org.icedamericanomall.ai.app.chat.ChatPrincipal;
import org.icedamericanomall.ai.app.command.SendChatMessageCommand;
import org.icedamericanomall.ai.app.dto.ChatConversationDTO;
import org.icedamericanomall.ai.app.dto.ChatMessageDTO;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * 会话消息装配器（适配层转换器）—— 报文 + STOMP 会话身份 → 应用层命令；
 * 应用层 DTO → 视图对象。
 *
 * <p>身份字段只从 {@link ChatPrincipal} 取；报文中的同名字段不存在，客户端无法伪造发送者。
 * 出参 VO 的装配只在本转换器完成（控制器不手写字段拷贝）。
 */
@Slf4j
@Component
public class ChatMessageConvertor {

    /**
     * 装配发送命令。发送者身份取自 STOMP 会话，报文只提供会话ID/接收者/内容。
     *
     * @param req       客户端报文
     * @param principal STOMP 会话身份
     * @return 应用层命令
     * @throws UnauthorizedException 会话身份缺失（未认证连接）
     */
    public SendChatMessageCommand toCommand(ChatSendReq req, Principal principal) {
        ChatPrincipal sender = resolvePrincipal(principal);
        return new SendChatMessageCommand(req.getConversationId(), sender.userId(), sender.senderRole(),
                req.getReceiverId(), req.getContent(), req.getContentType());
    }

    /**
     * 会话消息 DTO → 视图对象（REST 历史消息与 STOMP 广播共用）。
     *
     * @param dto 应用层消息 DTO
     * @return 视图对象；入参为 null 时返回 null
     */
    public ChatMessageVO toVO(ChatMessageDTO dto) {
        if (dto == null) {
            return null;
        }
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(dto.getId());
        vo.setMessageId(dto.getMessageId());
        vo.setConversationId(dto.getConversationId());
        vo.setSenderId(dto.getSenderId());
        vo.setSenderRole(dto.getSenderRole());
        vo.setReceiverId(dto.getReceiverId());
        vo.setContent(dto.getContent());
        vo.setContentType(dto.getContentType());
        vo.setExtra(dto.getExtra());
        vo.setIsRead(dto.getIsRead());
        vo.setIsAiGenerated(dto.getIsAiGenerated());
        vo.setCreateTime(dto.getCreateTime());
        return vo;
    }

    /** 会话消息 DTO 列表 → 视图对象列表。 */
    public List<ChatMessageVO> toVOList(List<ChatMessageDTO> dtoList) {
        return dtoList == null ? List.of() : dtoList.stream().map(this::toVO).toList();
    }

    /**
     * 会话摘要 DTO → 视图对象（{@code GET /api/chat/conversations}）。
     *
     * @param dto 应用层聚合结果
     * @return 视图对象（对外仅 4 个字段）
     */
    public ChatConversationVO toConversationVO(ChatConversationDTO dto) {
        if (dto == null) {
            return null;
        }
        ChatConversationVO vo = new ChatConversationVO();
        vo.setConversationId(dto.getConversationId());
        vo.setLastMessage(dto.getLastMessage());
        vo.setLastTime(dto.getLastTime());
        vo.setUnreadCount(dto.getUnreadCount());
        return vo;
    }

    /** 会话摘要 DTO 列表 → 视图对象列表。 */
    public List<ChatConversationVO> toConversationVOList(List<ChatConversationDTO> dtoList) {
        return dtoList == null ? List.of() : dtoList.stream().map(this::toConversationVO).toList();
    }

    private static ChatPrincipal resolvePrincipal(Principal principal) {
        if (principal instanceof ChatPrincipal chatPrincipal) {
            return chatPrincipal;
        }
        log.warn("STOMP 会话缺少已认证身份，拒绝发送消息");
        throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "会话未认证，无法发送消息");
    }
}
