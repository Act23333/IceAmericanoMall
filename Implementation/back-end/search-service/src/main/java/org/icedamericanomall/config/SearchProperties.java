package org.icedamericanomall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * search-service 配置 — ES 开关、连接参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "search.elasticsearch")
public class SearchProperties {
    private boolean enabled = false;
    private String host = "localhost";
    private int port = 9200;
}
