package org.noLazy.common.client.storage.impl;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.storage.StorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * Mock 对象存储 — minio.enabled=false（默认）时使用。
 * 返回占位 URL，不做真实上传。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = true)
public class MockStorageClient implements StorageClient {

    @Override
    public String upload(String bucket, String objectName, InputStream data, String contentType) {
        String url = "https://placeholder.icedmall.local/" + bucket + "/" + UUID.randomUUID() + "_" + objectName;
        log.info("MockStorageClient upload: {} -> {}", objectName, url);
        return url;
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        log.info("MockStorageClient download: {}/{} (no-op)", bucket, objectName);
        return InputStream.nullInputStream();
    }

    @Override
    public String getUrl(String bucket, String objectName) {
        return "https://placeholder.icedmall.local/" + bucket + "/" + objectName;
    }
}
