package org.icedAmericanoMall.integration.storage;

import java.io.InputStream;

/**
 * 对象存储客户端接口 — 文件上传 / 下载抽象。
 */
public interface StorageClient {

    String upload(String bucket, String objectName, InputStream data, String contentType);

    InputStream download(String bucket, String objectName);

    String getUrl(String bucket, String objectName);
}
