package org.icedamericanomall.integration.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * Mock 对象存储 — {@code minio.enabled=false}（默认）时使用。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "minio.enabled", havingValue = "false", matchIfMissing = true)
public class MockStorageClient implements StorageClient {

    @Override
    public String upload(String bucket, String objectName, InputStream data, String contentType) {
        String url = "https://placeholder.icedmall.local/" + bucket + "/" + UUID.randomUUID() + "_" + objectName;
        log.info("MockStorage upload: {} -> {}", objectName, url);
        return url;
    }

    @Override
    public InputStream download(String bucket, String objectName) {
        log.info("MockStorage download: {}/{} (no-op)", bucket, objectName);
        return InputStream.nullInputStream();
    }

    @Override
    public String getUrl(String bucket, String objectName) {
        return "https://placeholder.icedmall.local/" + bucket + "/" + objectName;
    }
}
