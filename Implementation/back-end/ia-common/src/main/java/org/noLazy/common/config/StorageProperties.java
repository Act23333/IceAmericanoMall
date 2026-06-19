package org.noLazy.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO / OSS 对象存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class StorageProperties {
    private boolean enabled = false;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
}
