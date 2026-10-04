package org.icedamericanomall.integration.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储配置（前缀 {@code minio}）。
 *
 * <p><b>V5.3 安全修正</b>：{@code accessKey} / {@code secretKey} 不再内置
 * {@code minioadmin} 默认值 —— 内置默认凭证意味着「忘了配也能连上」，是共享口令上生产的经典路径。
 * 现在必须由使用方显式配置（现有 user-service 的 {@code application.yml} 已显式写入，
 * 本地联调不受影响；未配置时 {@code MinioStorageClient} 初始化失败并记录 ERROR，
 * 上传/下载按降级语义返回 null）。
 */
@Data
@Component
@ConfigurationProperties(prefix = StorageProperties.PREFIX)
public class StorageProperties {

    /** 配置前缀（装配条件判定与绑定共用）。 */
    public static final String PREFIX = "minio";

    /** 是否启用 MinIO 真实实现（false/缺省 → MockStorageClient）。 */
    private boolean enabled = false;

    /** 服务端点。 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 —— 无默认值，必须显式配置（禁止内置默认凭证）。 */
    private String accessKey;

    /** 密钥 —— 无默认值，必须显式配置（禁止内置默认凭证）。 */
    private String secretKey;

    /** 默认桶名。 */
    private String bucket = "icedmall";
}
