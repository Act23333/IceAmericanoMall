package org.icedAmericanoMall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    /** 全局 AI 开关 — 关闭时所有 Agent bean 不创建，使用降级响应 */
    private boolean enabled = false;
}
