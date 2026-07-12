package org.icedAmericanoMall.integration.storage;

import java.io.InputStream;

/**
 * 对象存储客户端接口 — 文件上传 / 下载抽象。
 */
public interface StorageClient {

    String upload(String bucket, String objectName, InputStream data, String contentType);

    InputStream download(String bucket, String objectName);

    String getUrl(String bucket, String objectName);

    /** 生成预签名下载 URL（有效期内临时访问，不暴露直接连接）。 */
    default String getPresignedUrl(String bucket, String objectName, int expiryMinutes) {
        return getUrl(bucket, objectName); // 默认回退到直连
    }
}
