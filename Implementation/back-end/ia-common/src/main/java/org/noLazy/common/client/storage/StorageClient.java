package org.noLazy.common.client.storage;

import java.io.InputStream;

/**
 * 对象存储客户端接口 — 文件上传/下载抽象。
 */
public interface StorageClient {

    /**
     * 上传文件，返回可访问的 URL。
     */
    String upload(String bucket, String objectName, InputStream data, String contentType);

    /**
     * 下载文件。
     */
    InputStream download(String bucket, String objectName);

    /**
     * 获取文件访问 URL。
     */
    String getUrl(String bucket, String objectName);
}
