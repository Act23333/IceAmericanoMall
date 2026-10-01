package org.icedamericanomall.integration.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.UUID;

/**
 * Mock 对象存储 — {@code minio.enabled=false}（默认）时使用。
 *
 * <p>条件装配对齐（V5.2）：Mock 侧 {@code havingValue = "false", matchIfMissing = true}，
 * 与 {@code MockSmsClient} / {@code MockEmailClient} 完全一致。
 *
 * <p>去掉 {@code @Primary}（V5.2）：Mock 与 {@link MinioStorageClient} 的条件互斥，
 * 正常只会存在一个实现，{@code @Primary} 无作用却埋了一颗雷 —— 一旦互斥失效
 * （例如新增真实实现时漏标注解），{@code @Primary} 会让 Mock 静默胜出，
 * 把「上传失败」伪装成「上传成功」。去掉后该场景会直接抛
 * {@code NoUniqueBeanDefinitionException} 启动失败，属于期望的 fail-fast。
 * 该约定与 {@code MockEmailClient} 的既有做法一致。
 */
@Slf4j
@Component
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
