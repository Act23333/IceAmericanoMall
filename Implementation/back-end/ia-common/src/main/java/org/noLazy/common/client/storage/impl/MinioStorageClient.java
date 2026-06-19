package org.noLazy.common.client.storage.impl;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.storage.StorageClient;
import org.noLazy.common.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinIO 对象存储实现 — 仅在 minio.enabled=true 时激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class MinioStorageClient implements StorageClient {

    private final StorageProperties properties;
    private MinioClient client;

    public MinioStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            this.client = MinioClient.builder()
                    .endpoint(properties.getEndpoint())
                    .credentials(properties.getAccessKey(), properties.getSecretKey())
                    .build();
            log.info("MinIO client initialized: endpoint={}", properties.getEndpoint());
        } catch (Exception e) {
            log.error("MinIO client init failed", e);
        }
    }

    @Override
    public String upload(String bucket, String objectName, InputStream data, String contentType) {
        if (client == null) {
            log.error("MinIO client not initialized, upload skipped: {}/{}", bucket, objectName);
            return null;
        }
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(data, data.available(), -1)
                    .contentType(contentType)
                    .build());
            String url = getUrl(bucket, objectName);
            log.info("Uploaded to MinIO: {}/{}", bucket, objectName);
            return url;
        } catch (Exception e) {
            log.error("MinIO upload failed: {}/{}", bucket, objectName, e);
            return null;
        }
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        if (client == null) return null;
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO download failed: {}/{}", bucket, objectName, e);
            return null;
        }
    }

    @Override
    public String getUrl(String bucket, String objectName) {
        return properties.getEndpoint() + "/" + bucket + "/" + objectName;
    }
}
