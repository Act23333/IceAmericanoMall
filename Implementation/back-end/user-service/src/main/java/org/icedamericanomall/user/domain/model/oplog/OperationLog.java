package org.icedamericanomall.user.domain.model.oplog;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（审计台账领域模型，ADR-032 管道二）—— user 域管理操作的落库记录。
 *
 * <p>纯 POJO（零框架依赖）；持久化映射在
 * {@code infrastructure.persistence.oplog.OperationLogRepositoryImpl} 内联完成。
 */
@Data
@Builder
public class OperationLog {

    /** 技术主键 */
    private Long id;

    /** 操作人技术主键（用户维度检索用；系统/匿名调用为空） */
    private Long userId;

    /** 操作人用户名（登录态快照） */
    private String username;

    /** 动作标识（{@code MODULE:ACTION}，如 {@code USER:UPDATE_USER_STATUS}） */
    private String action;

    /** 操作目标（目标标识等非敏感信息） */
    private String target;

    /** 操作摘要（不得包含手机号/密文等敏感值） */
    private String detail;

    /** 客户端 IP（登录态快照） */
    private String ip;

    /** 落库时间（DB 默认值填充） */
    private LocalDateTime createTime;
}
