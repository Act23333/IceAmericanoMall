package org.icedamericanomall.user.app.port;

/**
 * 头像对象存储（应用层端口，基础设施层用 MinIO + Thumbnailator 实现）。
 *
 * <p>对象命名约定：原图 {@code avatar/{userId}.jpg}，
 * 缩略图 {@code avatar/{userId}_{w}x{h}.jpg}（200x200 / 50x50）。
 */
public interface AvatarStorage {

    /**
     * 上传头像原图并生成多尺寸缩略图。
     *
     * @param userId      用户 ID（对象命名）
     * @param data        图片字节（原始上传内容，JPG 化处理由实现完成）
     * @param contentType 原始 Content-Type
     * @return 原图可访问 URL
     * @throws org.icedamericanomall.common.exception.BizException 图片解析/存储失败
     */
    String store(Long userId, byte[] data, String contentType);

    /**
     * 下载头像（按尺寸降级：缩略图 → 原图 → 空数组）。
     *
     * @param userId 用户 ID
     * @param width  期望宽
     * @param height 期望高
     * @return 图片字节
     */
    byte[] load(Long userId, int width, int height);
}
