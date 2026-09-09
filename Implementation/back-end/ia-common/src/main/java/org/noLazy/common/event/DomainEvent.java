package org.noLazy.common.event;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 领域事件基类 — RocketMQ 消息体。
 *
 * 阿里标准: common 模块定义事件契约，各服务持有 Producer/Consumer。
 */
@Getter
public abstract class DomainEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String eventId = UUID.randomUUID().toString().replace("-", "");
    private final LocalDateTime timestamp = LocalDateTime.now();
    /** 事件来源服务名 */
    @Setter
    private String source;
}
