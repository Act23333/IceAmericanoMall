package org.icedamericanomall.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.icedamericanomall.user.app.port.AvatarStorage;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 头像对象存储适配器（MinIO + Thumbnailator 实现）。
 *
 * <p>对象命名：原图 {@code avatar/{userId}.jpg}，缩略图 {@code avatar/{userId}_{w}x{h}.jpg}；
 * 生成 200x200 与 50x50 两档缩略图（原始实现语义，未变更）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarStorageAdapter implements AvatarStorage {

    /** 缩略图尺寸档位 */
    private static final int[][] THUMB_SIZES = {{200, 200}, {50, 50}};

    private static final String AVATAR_OBJECT_PREFIX = "avatar/";
    private static final String IMAGE_EXT = "jpg";

    private final StorageClient storageClient;
    private final StorageProperties storageProperties;

    @Override
    public String store(Long userId, byte[] data, String contentType) {
        try {
            String bucket = storageProperties.getBucket();
            String originalObject = AVATAR_OBJECT_PREFIX + userId + "." + IMAGE_EXT;
            storageClient.upload(bucket, originalObject, new ByteArrayInputStream(data), contentType);

            for (int[] size : THUMB_SIZES) {
                byte[] thumb = resize(data, size[0], size[1]);
                String thumbObject = AVATAR_OBJECT_PREFIX + userId + "_" + size[0] + "x" + size[1] + "." + IMAGE_EXT;
                storageClient.upload(bucket, thumbObject, new ByteArrayInputStream(thumb), "image/jpeg");
            }
            return storageClient.getUrl(bucket, originalObject);
        } catch (IOException e) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "头像上传失败");
        }
    }

    @Override
    public byte[] load(Long userId, int width, int height) {
        String bucket = storageProperties.getBucket();
        String thumbObject = AVATAR_OBJECT_PREFIX + userId + "_" + width + "x" + height + "." + IMAGE_EXT;
        String originalObject = AVATAR_OBJECT_PREFIX + userId + "." + IMAGE_EXT;

        InputStream is = storageClient.download(bucket, thumbObject);
        if (is == null) {
            is = storageClient.download(bucket, originalObject);
        }
        if (is == null) {
            return new byte[0];
        }
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private byte[] resize(byte[] input, int width, int height) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(input));
        if (img == null) {
            throw new IOException("无法解析图片");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(img).size(width, height).outputFormat(IMAGE_EXT).toOutputStream(out);
        return out.toByteArray();
    }
}
